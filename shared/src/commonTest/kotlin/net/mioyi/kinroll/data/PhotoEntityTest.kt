package net.mioyi.kinroll.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoKind

class PhotoEntityTest {
    @Test
    fun fromAssetCopiesEveryMetadataFieldWithoutTransformingIt() {
        val asset = populatedAsset()
        val entity = PhotoEntity.fromAsset(asset)

        assertEquals(asset.id, entity.id)
        assertEquals(asset.uri, entity.uri)
        assertEquals(asset.fileName, entity.fileName)
        assertEquals(asset.mimeType, entity.mimeType)
        assertEquals(asset.sizeBytes, entity.sizeBytes)
        assertEquals(asset.width, entity.width)
        assertEquals(asset.height, entity.height)
        assertEquals(asset.orientation, entity.orientation)
        assertEquals(asset.takenAt, entity.takenAt)
        assertEquals(asset.addedAt, entity.addedAt)
        assertEquals(asset.modifiedAt, entity.modifiedAt)
        assertEquals(asset.album, entity.album)
        assertEquals(asset.relativePath, entity.relativePath)
        assertEquals(asset.ownerPackage, entity.ownerPackage)
        assertEquals(asset.isFavorite, entity.isFavorite)
        assertEquals(asset.isScreenshotHint, entity.isScreenshotHint)
        assertEquals(asset.latitude, entity.latitude)
        assertEquals(asset.longitude, entity.longitude)
    }

    @Test
    fun fromAssetPreservesMissingMetadataAndFalseFlags() {
        val entity = PhotoEntity.fromAsset(
            populatedAsset().copy(
                fileName = null,
                mimeType = null,
                takenAt = null,
                addedAt = null,
                modifiedAt = null,
                album = null,
                relativePath = null,
                ownerPackage = null,
                isFavorite = false,
                isScreenshotHint = false,
                latitude = null,
                longitude = null,
            ),
        )

        assertNull(entity.fileName)
        assertNull(entity.mimeType)
        assertNull(entity.takenAt)
        assertNull(entity.addedAt)
        assertNull(entity.modifiedAt)
        assertNull(entity.album)
        assertNull(entity.relativePath)
        assertNull(entity.ownerPackage)
        assertFalse(entity.isFavorite)
        assertFalse(entity.isScreenshotHint)
        assertNull(entity.latitude)
        assertNull(entity.longitude)
    }

    @Test
    fun fromAssetLeavesEnrichmentAndIndexStateUninitializedEvenWithScreenshotHint() {
        val entity = PhotoEntity.fromAsset(populatedAsset())

        assertTrue(entity.isScreenshotHint)
        assertEquals(PhotoKind.UNKNOWN.name, entity.kind)
        assertNull(entity.cameraMake)
        assertNull(entity.cameraModel)
        assertNull(entity.lensModel)
        assertNull(entity.iso)
        assertNull(entity.exposureTime)
        assertNull(entity.fNumber)
        assertNull(entity.focalLength)
        assertNull(entity.dHash)
        assertNull(entity.blurScore)
        assertNull(entity.brightness)
        assertNull(entity.clipEmbedding)
        assertNull(entity.category)
        assertNull(entity.categoryScore)
        assertNull(entity.ocrText)
        assertNull(entity.ocrJson)
        assertFalse(entity.isBadShot)
        assertEquals(0, entity.badShotReasons)
        assertEquals(0, entity.indexVersion)
        assertNull(entity.indexedAt)
        assertNull(entity.indexError)
    }

    private fun populatedAsset() = PhotoAsset(
        id = "asset/42",
        uri = "content://media/external/images/media/42",
        fileName = "IMG_0042.JPG",
        mimeType = "image/jpeg",
        sizeBytes = 5_000_000_000L,
        width = 4032,
        height = 3024,
        orientation = 90,
        takenAt = 1_700_000_000_001L,
        addedAt = 1_700_000_000_002L,
        modifiedAt = 1_700_000_000_003L,
        album = "Camera",
        relativePath = "DCIM/Camera/",
        ownerPackage = "net.example.camera",
        isFavorite = true,
        isScreenshotHint = true,
        latitude = -33.8688,
        longitude = 151.2093,
    )
}
