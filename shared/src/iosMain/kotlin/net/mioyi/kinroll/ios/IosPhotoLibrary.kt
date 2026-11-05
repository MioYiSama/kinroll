package net.mioyi.kinroll.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mioyi.kinroll.platform.*
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal fun permissionState(status: Int): PermissionState = when (status) {
    0 -> PermissionState.NOT_DETERMINED
    3 -> PermissionState.FULL
    4 -> PermissionState.LIMITED
    else -> PermissionState.DENIED
}
class IosPhotoLibrary(private val bridge: PhotoKitBridge) : PhotoLibrary {
    override fun permissionState(): PermissionState = permissionState(bridge.authorizationStatus())
    override suspend fun listAssets(): List<PhotoAsset> = withContext(Dispatchers.Default) { bridge.fetchAssets() }
    override suspend fun readDetails(id: String): PhotoDetails? = withContext(Dispatchers.Default) { bridge.readDetails(id) }
    override suspend fun loadPixels(id: String, maxSide: Int): RgbaImage? = withContext(Dispatchers.Default) {
        require(maxSide > 0)
        bridge.loadPixels(id, maxSide)?.let { RgbaImage(it.width, it.height, it.rgba.toByteArray()) }
    }
    override suspend fun loadThumbnail(id: String, maxSide: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val pixels = loadPixels(id, maxSide) ?: return@withContext null
        Image.makeRaster(ImageInfo(pixels.width, pixels.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            pixels.pixels, pixels.width * 4).toComposeImageBitmap()
    }
}
