package net.mioyi.kinroll

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.mioyi.kinroll.cleanup.CleanupRepository
import net.mioyi.kinroll.data.KinrollDatabase
import net.mioyi.kinroll.data.PhotoDao
import net.mioyi.kinroll.data.SettingsRepository
import net.mioyi.kinroll.index.Indexer
import net.mioyi.kinroll.llm.LlmService
import net.mioyi.kinroll.ml.clip.ClipEngine
import net.mioyi.kinroll.ml.ocr.OcrEngine
import net.mioyi.kinroll.model.ModelManager
import net.mioyi.kinroll.model.createHttpClient
import net.mioyi.kinroll.platform.IndexScheduler
import net.mioyi.kinroll.platform.PlatformServices
import net.mioyi.kinroll.post.PostGenerator
import net.mioyi.kinroll.search.EmbeddingIndex
import net.mioyi.kinroll.search.SearchService
import net.mioyi.kinroll.ui.ThumbnailCache

/** Hand-written dependency graph; every member is created on first use. */
class AppGraph(val platform: PlatformServices) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: KinrollDatabase by lazy {
        platform.databaseBuilder.setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default).build()
    }
    val photoDao: PhotoDao by lazy { database.photoDao() }
    val settings: SettingsRepository by lazy { SettingsRepository(database.settingDao()) }
    private val httpClient: HttpClient by lazy { createHttpClient(platform.httpEngine) }
    val modelManager: ModelManager by lazy { ModelManager(platform.filesDir, settings, httpClient) }
    val clip: ClipEngine by lazy { ClipEngine(platform.inferenceRuntime, modelManager) }
    val ocr: OcrEngine by lazy { OcrEngine(platform.inferenceRuntime, modelManager) }
    val llm: LlmService by lazy { LlmService(platform.llamaNative, modelManager, platform.cpuThreads) }
    val embeddingIndex: EmbeddingIndex by lazy { EmbeddingIndex(photoDao) }
    val indexer: Indexer by lazy {
        Indexer(platform.photoLibrary, photoDao, modelManager, clip, ocr, embeddingIndex)
    }
    val indexScheduler: IndexScheduler by lazy { platform.indexSchedulerFactory(this) }
    val search: SearchService by lazy { SearchService(photoDao, clip, llm, embeddingIndex) }
    val cleanup: CleanupRepository by lazy { CleanupRepository(photoDao, embeddingIndex) }
    val posts: PostGenerator by lazy { PostGenerator(photoDao, llm) }
    val thumbnails: ThumbnailCache by lazy { ThumbnailCache(platform.photoLibrary) }
}
