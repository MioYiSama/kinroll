package net.mioyi.kinroll.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.room.RoomDatabase
import io.ktor.client.engine.HttpClientEngineFactory
import net.mioyi.kinroll.AppGraph
import net.mioyi.kinroll.data.KinrollDatabase

enum class PermissionState { NOT_DETERMINED, DENIED, LIMITED, FULL }
enum class PhotoKind { UNKNOWN, CAMERA, SCREENSHOT, SAVED }

data class PhotoAsset(
    val id: String, val uri: String, val fileName: String?, val mimeType: String?,
    val sizeBytes: Long, val width: Int, val height: Int, val orientation: Int,
    val takenAt: Long?, val addedAt: Long?, val modifiedAt: Long?,
    val album: String?, val relativePath: String?, val ownerPackage: String?,
    val isFavorite: Boolean, val isScreenshotHint: Boolean,
    val latitude: Double?, val longitude: Double?,
)
data class PhotoDetails(
    val cameraMake: String?, val cameraModel: String?, val lensModel: String?,
    val iso: Int?, val exposureTime: Double?, val fNumber: Double?, val focalLength: Double?,
    val latitude: Double?, val longitude: Double?, val sizeBytes: Long?,
)
class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray)
data class PhotoRef(val id: String, val uri: String)

interface PhotoLibrary {
    fun permissionState(): PermissionState
    suspend fun listAssets(): List<PhotoAsset>
    suspend fun readDetails(id: String): PhotoDetails?
    suspend fun loadPixels(id: String, maxSide: Int): RgbaImage?
    suspend fun loadThumbnail(id: String, maxSide: Int): ImageBitmap?
}

class TensorInput(val name: String, val shape: LongArray, val floats: FloatArray? = null, val longs: LongArray? = null)
class TensorOutput(val shape: LongArray, val data: FloatArray)
interface InferenceSession : AutoCloseable {
    val inputNames: List<String>
    val outputNames: List<String>
    fun run(inputs: List<TensorInput>, outputs: List<String>): List<TensorOutput>
}
interface InferenceRuntime { fun open(modelPath: String): InferenceSession }

data class SamplingParams(val temperature: Float, val topP: Float, val topK: Int, val presencePenalty: Float, val seed: Int)
interface LlamaNative {
    fun load(modelPath: String, contextSize: Int, threads: Int): String?
    fun begin(prompt: String, params: SamplingParams): String?
    fun next(): ByteArray?
    fun unload()
}

interface IndexScheduler { fun request() }

class PlatformServices(
    val photoLibrary: PhotoLibrary,
    val inferenceRuntime: InferenceRuntime,
    val llamaNative: LlamaNative,
    val databaseBuilder: RoomDatabase.Builder<KinrollDatabase>,
    val filesDir: String,
    val httpEngine: HttpClientEngineFactory<*>,
    val cpuThreads: Int,
    val indexSchedulerFactory: (AppGraph) -> IndexScheduler,
)

@Composable expect fun rememberPermissionRequester(onResult: (PermissionState) -> Unit): () -> Unit
@Composable expect fun rememberTrashRequester(onResult: (trashedIds: List<String>) -> Unit): (List<PhotoRef>) -> Unit
expect fun plainTextClipEntry(text: String): ClipEntry
