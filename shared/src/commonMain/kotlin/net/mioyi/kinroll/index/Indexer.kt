package net.mioyi.kinroll.index

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import net.mioyi.kinroll.data.BadShotReason
import net.mioyi.kinroll.data.EmbeddingCodec
import net.mioyi.kinroll.data.PhotoDao
import net.mioyi.kinroll.data.PhotoEntity
import net.mioyi.kinroll.data.SyncInfo
import net.mioyi.kinroll.ml.clip.ClipEngine
import net.mioyi.kinroll.ml.clip.PhotoCategory
import net.mioyi.kinroll.ml.image.Quality
import net.mioyi.kinroll.ml.ocr.OcrEngine
import net.mioyi.kinroll.model.ModelManager
import net.mioyi.kinroll.platform.PermissionState
import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoKind
import net.mioyi.kinroll.platform.PhotoLibrary
import net.mioyi.kinroll.search.EmbeddingIndex
import kotlin.time.Clock
import kotlin.time.TimeSource

const val INDEX_VERSION = 1

internal fun needsResync(old: SyncInfo?, asset: PhotoAsset): Boolean =
    old == null || old.modifiedAt != asset.modifiedAt ||
        (asset.sizeBytes > 0 && old.sizeBytes != asset.sizeBytes)

sealed interface IndexerState {
    data object Idle : IndexerState
    data object Syncing : IndexerState
    data class Indexing(val done: Int, val total: Int) : IndexerState
    data class Failed(val message: String) : IndexerState
}

class Indexer(
    private val photoLibrary: PhotoLibrary,
    private val photoDao: PhotoDao,
    private val modelManager: ModelManager,
    private val clip: ClipEngine,
    private val ocr: OcrEngine,
    private val embeddingIndex: EmbeddingIndex,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<IndexerState>(IndexerState.Idle)
    val state: StateFlow<IndexerState> = mutableState.asStateFlow()

    suspend fun runOnce() {
        if (!mutex.tryLock()) return
        try {
            val permission = photoLibrary.permissionState()
            if ((permission != PermissionState.FULL && permission != PermissionState.LIMITED) || !modelManager.allReady()) return
            withContext(Dispatchers.Default) {
                mutableState.value = IndexerState.Syncing
                val assets = photoLibrary.listAssets().associateBy { it.id }
                val previous = photoDao.syncInfo().associateBy { it.id }
                val changed = assets.values.filter { asset -> needsResync(previous[asset.id], asset) }
                for (batch in changed.chunked(500)) photoDao.insertAll(batch.map { PhotoEntity.fromAsset(it) })
                embeddingIndex.remove(changed.map { it.id })
                val removed = previous.keys.filterNot(assets::containsKey)
                for (batch in removed.chunked(500)) photoDao.deleteByIds(batch)
                embeddingIndex.remove(removed)
                val total = (photoDao.observeCount().first() - photoDao.observeIndexedCount(INDEX_VERSION).first()).coerceAtLeast(0)
                var done = 0
                mutableState.value = IndexerState.Indexing(done, total)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val pending = photoDao.pendingIds(INDEX_VERSION, 8)
                    if (pending.isEmpty()) break
                    for (id in pending) {
                        val entity = photoDao.getById(id) ?: continue
                        val asset = assets[id] ?: continue
                        indexPhoto(entity, asset)
                        done++
                        mutableState.value = IndexerState.Indexing(done, total)
                        currentCoroutineContext().ensureActive()
                    }
                }
                mutableState.value = IndexerState.Idle
            }
        } catch (e: CancellationException) {
            mutableState.value = IndexerState.Idle
            throw e
        } catch (e: Exception) {
            mutableState.value = IndexerState.Failed(e.message ?: e.toString())
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun indexPhoto(entity: PhotoEntity, asset: PhotoAsset) {
        val start = TimeSource.Monotonic.markNow()
        var indexedEmbedding: FloatArray? = null
        try {
            val details = photoLibrary.readDetails(entity.id)
            val kind = KindResolver.resolve(asset, details)
            entity.kind = kind.name
            if (details != null) {
                entity.cameraMake = details.cameraMake
                entity.cameraModel = details.cameraModel
                entity.lensModel = details.lensModel
                entity.iso = details.iso
                entity.exposureTime = details.exposureTime
                entity.fNumber = details.fNumber
                entity.focalLength = details.focalLength
                entity.latitude = details.latitude ?: entity.latitude
                entity.longitude = details.longitude ?: entity.longitude
                details.sizeBytes?.let { entity.sizeBytes = it }
            }
            val image = photoLibrary.loadPixels(entity.id, 1280)
            if (image == null) {
                entity.indexError = "unavailable"
            } else {
                val quality = Quality.analyze(image)
                entity.dHash = quality.dHash
                entity.blurScore = quality.blurScore
                entity.brightness = quality.brightness
                val embedding = clip.imageEmbedding(image)
                indexedEmbedding = embedding
                entity.clipEmbedding = EmbeddingCodec.encode(embedding)
                val category = clip.classify(embedding)
                entity.category = category.category.name
                entity.categoryScore = category.score
                var reasons = 0
                if (quality.blurScore < 60f) reasons = reasons or BadShotReason.BLUR
                if (quality.brightness < 0.12f) reasons = reasons or BadShotReason.DARK
                if (quality.brightness > 0.92f) reasons = reasons or BadShotReason.OVEREXPOSED
                if (clip.accidentalProbability(embedding) >= 0.70f) reasons = reasons or BadShotReason.ACCIDENTAL
                entity.badShotReasons = reasons
                entity.isBadShot = kind == PhotoKind.CAMERA && reasons != 0
                if (kind == PhotoKind.SCREENSHOT || kind == PhotoKind.SAVED ||
                    category.category == PhotoCategory.DOCUMENT || category.category == PhotoCategory.RECEIPT
                ) {
                    val result = ocr.recognize(image)
                    entity.ocrText = result.text
                    entity.ocrJson = result.toJson()
                }
                entity.indexError = null
            }
            entity.indexVersion = INDEX_VERSION
            entity.indexedAt = Clock.System.now().toEpochMilliseconds()
            photoDao.update(entity)
            indexedEmbedding?.let { embeddingIndex.put(entity.id, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            entity.indexError = e.message ?: e.toString()
            entity.indexVersion = INDEX_VERSION
            entity.indexedAt = Clock.System.now().toEpochMilliseconds()
            photoDao.update(entity)
            indexedEmbedding?.let { embeddingIndex.put(entity.id, it) }
        } finally {
            println("kinroll-index id=${entity.id} kind=${entity.kind} category=${entity.category} ocrChars=${entity.ocrText?.length ?: 0} ms=${start.elapsedNow().inWholeMilliseconds}")
        }
    }
}
