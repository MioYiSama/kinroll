package net.mioyi.kinroll.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoKind

@Entity(
    tableName = "photo",
    indices = [Index("kind"), Index("category"), Index("isBadShot"), Index("indexVersion")],
)
class PhotoEntity(
    @PrimaryKey val id: String,
    var uri: String,
    var fileName: String?,
    var mimeType: String?,
    var sizeBytes: Long,
    var width: Int,
    var height: Int,
    var orientation: Int,
    var takenAt: Long?,
    var addedAt: Long?,
    var modifiedAt: Long?,
    var album: String?,
    var relativePath: String?,
    var ownerPackage: String?,
    var isFavorite: Boolean,
    var isScreenshotHint: Boolean,
    var latitude: Double?,
    var longitude: Double?,
    var cameraMake: String? = null,
    var cameraModel: String? = null,
    var lensModel: String? = null,
    var iso: Int? = null,
    var exposureTime: Double? = null,
    var fNumber: Double? = null,
    var focalLength: Double? = null,
    var kind: String = PhotoKind.UNKNOWN.name,
    var dHash: Long? = null,
    var blurScore: Float? = null,
    var brightness: Float? = null,
    var clipEmbedding: ByteArray? = null,
    var category: String? = null,
    var categoryScore: Float? = null,
    var ocrText: String? = null,
    var ocrJson: String? = null,
    var isBadShot: Boolean = false,
    var badShotReasons: Int = 0,
    var indexVersion: Int = 0,
    var indexedAt: Long? = null,
    var indexError: String? = null,
) {
    companion object {
        fun fromAsset(a: PhotoAsset): PhotoEntity = PhotoEntity(
            id = a.id,
            uri = a.uri,
            fileName = a.fileName,
            mimeType = a.mimeType,
            sizeBytes = a.sizeBytes,
            width = a.width,
            height = a.height,
            orientation = a.orientation,
            takenAt = a.takenAt,
            addedAt = a.addedAt,
            modifiedAt = a.modifiedAt,
            album = a.album,
            relativePath = a.relativePath,
            ownerPackage = a.ownerPackage,
            isFavorite = a.isFavorite,
            isScreenshotHint = a.isScreenshotHint,
            latitude = a.latitude,
            longitude = a.longitude,
        )
    }
}

object BadShotReason {
    const val BLUR = 1
    const val DARK = 2
    const val OVEREXPOSED = 4
    const val ACCIDENTAL = 8
}
