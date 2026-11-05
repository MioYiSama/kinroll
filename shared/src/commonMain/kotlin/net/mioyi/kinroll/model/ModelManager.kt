package net.mioyi.kinroll.model

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.mioyi.kinroll.data.SettingsRepository

data class DownloadProgress(
    val fileId: String,
    val fileBytes: Long,
    val fileTotal: Long,
    val overallBytes: Long,
    val overallTotal: Long,
)

fun createHttpClient(engine: HttpClientEngineFactory<*>): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
    }
}

class ModelManager(
    private val filesDir: String,
    private val settings: SettingsRepository,
    private val httpClient: HttpClient,
) {
    private val mutex = Mutex()
    private val mutableReady = MutableStateFlow(allReady())
    val readyFlow: StateFlow<Boolean> = mutableReady.asStateFlow()

    fun localPath(id: String): String {
        val file = ModelManifest.file(id)
        return "$filesDir/models/$id/${file.path.substringAfterLast('/')}"
    }

    fun isReady(file: ModelFile): Boolean {
        val metadata = SystemFileSystem.metadataOrNull(Path(localPath(file.id))) ?: return false
        return metadata.isRegularFile && validSize(file, metadata.size)
    }

    fun allReady(): Boolean = ModelManifest.files.all(::isReady)

    suspend fun downloadAll(source: DownloadSource, onProgress: (DownloadProgress) -> Unit) {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                settings.setDownloadSource(source)
                val total = ModelManifest.files.sumOf { it.sizeBytes }
                var completed = 0L
                try {
                    for (file in ModelManifest.files) {
                        currentCoroutineContext().ensureActive()
                        if (!isReady(file)) {
                            Downloader(httpClient).download(file, source, Path(localPath(file.id))) { bytes ->
                                onProgress(DownloadProgress(file.id, bytes, file.sizeBytes, completed + bytes, total))
                            }
                        }
                        completed += file.sizeBytes
                        onProgress(DownloadProgress(file.id, file.sizeBytes, file.sizeBytes, completed, total))
                        mutableReady.value = allReady()
                    }
                } finally {
                    mutableReady.value = allReady()
                }
            }
        }
    }
}

private fun validSize(file: ModelFile, size: Long): Boolean =
    if (file.exactSize) size == file.sizeBytes else size > 0

internal class Downloader(private val client: HttpClient) {
    suspend fun download(file: ModelFile, source: DownloadSource, target: Path, progress: (Long) -> Unit) {
        target.parent?.let { SystemFileSystem.createDirectories(it) }
        val part = Path("$target.part")
        var retriedRange = false
        while (true) {
            val existing = SystemFileSystem.metadataOrNull(part)?.size ?: 0L
            var restart = false
            client.prepareGet(source.url(file.repo, file.path)) {
                if (existing > 0) header(HttpHeaders.Range, "bytes=$existing-")
            }.execute { response ->
                when (response.status) {
                    HttpStatusCode.RequestedRangeNotSatisfiable -> {
                        if (existing != file.sizeBytes) {
                            if (SystemFileSystem.exists(part)) SystemFileSystem.delete(part)
                            check(!retriedRange) { "range rejected: ${file.id}" }
                            restart = true
                        }
                    }
                    HttpStatusCode.OK, HttpStatusCode.PartialContent -> {
                        val append = response.status == HttpStatusCode.PartialContent && existing > 0
                        var bytes = if (append) existing else 0L
                        var reported = bytes
                        progress(bytes)
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(64 * 1024)
                        SystemFileSystem.sink(part, append = append).buffered().use { sink ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = channel.readAvailable(buffer, 0, buffer.size)
                                if (count == -1) break
                                if (count == 0) continue
                                sink.write(buffer, 0, count)
                                bytes += count
                                if (bytes - reported >= 512 * 1024) {
                                    progress(bytes)
                                    reported = bytes
                                }
                            }
                        }
                        channel.closedCause?.let { throw it }
                        progress(bytes)
                    }
                    else -> error("HTTP ${response.status.value}: ${file.id}")
                }
            }
            if (restart) {
                retriedRange = true
                continue
            }
            val size = SystemFileSystem.metadataOrNull(part)?.size ?: 0L
            if (!validSize(file, size)) {
                if (SystemFileSystem.exists(part)) SystemFileSystem.delete(part)
                error("size mismatch: ${file.id}")
            }
            SystemFileSystem.atomicMove(part, target)
            progress(size)
            return
        }
    }
}
