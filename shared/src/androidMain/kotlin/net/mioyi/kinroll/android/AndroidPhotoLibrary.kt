package net.mioyi.kinroll.android

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mioyi.kinroll.platform.PermissionState
import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoDetails
import net.mioyi.kinroll.platform.PhotoLibrary
import net.mioyi.kinroll.platform.RgbaImage

class AndroidPhotoLibrary(context: Context) : PhotoLibrary {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    override fun permissionState(): PermissionState {
        val fullPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return when {
            granted(fullPermission) -> PermissionState.FULL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PermissionState.LIMITED
            else -> PermissionState.DENIED
        }
    }

    override suspend fun listAssets(): List<PhotoAsset> = withContext(Dispatchers.IO) {
        val assets = ArrayList<PhotoAsset>()
        resolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            PROJECTION,
            "${MediaStore.Images.Media.IS_TRASHED} = 0",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val columns = PROJECTION.associateWith { cursor.getColumnIndexOrThrow(it) }
            fun column(name: String): Int = columns.getValue(name)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(column(MediaStore.Images.Media._ID)).toString()
                val fileName = cursor.stringOrNull(column(MediaStore.Images.Media.DISPLAY_NAME))
                val relativePath = cursor.stringOrNull(column(MediaStore.Images.Media.RELATIVE_PATH))
                assets += PhotoAsset(
                    id = id,
                    uri = photoUri(id).toString(),
                    fileName = fileName,
                    mimeType = cursor.stringOrNull(column(MediaStore.Images.Media.MIME_TYPE)),
                    sizeBytes = cursor.getLong(column(MediaStore.Images.Media.SIZE)),
                    width = cursor.getInt(column(MediaStore.Images.Media.WIDTH)),
                    height = cursor.getInt(column(MediaStore.Images.Media.HEIGHT)),
                    orientation = cursor.getInt(column(MediaStore.Images.Media.ORIENTATION)),
                    takenAt = cursor.longOrNull(column(MediaStore.Images.Media.DATE_TAKEN)),
                    addedAt = cursor.longOrNull(column(MediaStore.Images.Media.DATE_ADDED))?.times(1000L),
                    modifiedAt = cursor.longOrNull(column(MediaStore.Images.Media.DATE_MODIFIED))?.times(1000L),
                    album = cursor.stringOrNull(column(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)),
                    relativePath = relativePath,
                    ownerPackage = cursor.stringOrNull(column(MediaStore.Images.Media.OWNER_PACKAGE_NAME)),
                    isFavorite = cursor.getInt(column(MediaStore.Images.Media.IS_FAVORITE)) != 0,
                    isScreenshotHint = relativePath?.contains("screenshot", ignoreCase = true) == true ||
                        fileName?.startsWith("screenshot", ignoreCase = true) == true,
                    latitude = null,
                    longitude = null,
                )
            }
        }
        assets
    }

    override suspend fun readDetails(id: String): PhotoDetails? = withContext(Dispatchers.IO) {
        readable {
            val uri = photoUri(id).let {
                if (granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) MediaStore.setRequireOriginal(it) else it
            }
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val coordinates = exif.latLong
                PhotoDetails(
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                    iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 },
                    exposureTime = exif.number(ExifInterface.TAG_EXPOSURE_TIME),
                    fNumber = exif.number(ExifInterface.TAG_F_NUMBER),
                    focalLength = exif.number(ExifInterface.TAG_FOCAL_LENGTH),
                    latitude = coordinates?.get(0),
                    longitude = coordinates?.get(1),
                    sizeBytes = null,
                )
            }
        }
    }

    override suspend fun loadPixels(id: String, maxSide: Int): RgbaImage? = withContext(Dispatchers.IO) {
        readable {
            val bitmap = loadBitmap(id, maxSide)
            try {
                val stride = bitmap.rowBytes
                val packedStride = bitmap.width * 4
                val buffer = ByteBuffer.allocate(stride * bitmap.height)
                bitmap.copyPixelsToBuffer(buffer)
                val pixels = if (stride == packedStride) buffer.array() else {
                    ByteArray(packedStride * bitmap.height).also { packed ->
                        for (row in 0 until bitmap.height) {
                            buffer.array().copyInto(packed, row * packedStride, row * stride, row * stride + packedStride)
                        }
                    }
                }
                RgbaImage(bitmap.width, bitmap.height, pixels)
            } finally {
                bitmap.recycle()
            }
        }
    }

    override suspend fun loadThumbnail(id: String, maxSide: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        readable { loadBitmap(id, maxSide).asImageBitmap() }
    }

    private fun loadBitmap(id: String, maxSide: Int): Bitmap {
        val bitmap = resolver.loadThumbnail(photoUri(id), Size(maxSide, maxSide), null)
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return try {
            checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) { "Unable to copy thumbnail pixels" }
        } finally {
            bitmap.recycle()
        }
    }

    private fun granted(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun photoUri(id: String): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toLong())

    private inline fun <T> readable(block: () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun ExifInterface.number(tag: String): Double? = getAttributeDouble(tag, Double.NaN).takeIf { it.isFinite() }

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.OWNER_PACKAGE_NAME,
            MediaStore.Images.Media.IS_FAVORITE,
        )
    }
}
