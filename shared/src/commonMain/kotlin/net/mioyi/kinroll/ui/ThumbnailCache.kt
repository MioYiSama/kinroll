package net.mioyi.kinroll.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mioyi.kinroll.platform.PhotoLibrary

/** In-memory LRU of decoded thumbnails, keyed by `"$id@$maxSide"`. */
class ThumbnailCache(private val photoLibrary: PhotoLibrary, private val capacity: Int = 300) {
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, ImageBitmap>()

    suspend fun peek(id: String, maxSide: Int): ImageBitmap? = lock.withLock {
        val key = key(id, maxSide)
        val hit = entries.remove(key) ?: return@withLock null
        entries[key] = hit
        hit
    }

    suspend fun load(id: String, maxSide: Int): ImageBitmap? {
        peek(id, maxSide)?.let { return it }
        val bitmap = photoLibrary.loadThumbnail(id, maxSide) ?: return null
        lock.withLock {
            entries.remove(key(id, maxSide))
            entries[key(id, maxSide)] = bitmap
            while (entries.size > capacity) {
                entries.remove(entries.keys.first())
            }
        }
        return bitmap
    }

    private fun key(id: String, maxSide: Int) = "$id@$maxSide"
}
