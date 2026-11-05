package net.mioyi.kinroll.index

import net.mioyi.kinroll.data.SyncInfo
import net.mioyi.kinroll.platform.PhotoAsset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexerSyncTest {
    private fun asset(size: Long = 0, modifiedAt: Long? = 100) = PhotoAsset(
        id = "1", uri = "photo://1", fileName = "IMG.jpg", mimeType = "image/jpeg", sizeBytes = size,
        width = 100, height = 100, orientation = 0, takenAt = null, addedAt = null, modifiedAt = modifiedAt,
        album = null, relativePath = null, ownerPackage = null, isFavorite = false,
        isScreenshotHint = false, latitude = null, longitude = null,
    )

    @Test fun unknownListingSizeDoesNotInvalidateSizeFilledByDetails() {
        assertFalse(needsResync(SyncInfo("1", 100, 4096), asset(size = 0)))
    }

    @Test fun knownListingSizeChangesRequireResync() {
        assertTrue(needsResync(SyncInfo("1", 100, 4096), asset(size = 8192)))
        assertFalse(needsResync(SyncInfo("1", 100, 4096), asset(size = 4096)))
    }

    @Test fun modifiedDateChangesRequireResyncEvenWhenSizeUnknown() {
        assertTrue(needsResync(SyncInfo("1", 100, 4096), asset(modifiedAt = 101)))
        assertTrue(needsResync(SyncInfo("1", null, 4096), asset(modifiedAt = 100)))
    }

    @Test fun newAssetsRequireSyncEvenWhenSizeUnknown() {
        assertTrue(needsResync(null, asset()))
    }

    @Test fun unchangedUnknownMetadataDoesNotRequireSync() {
        assertFalse(needsResync(SyncInfo("1", null, 4096), asset(size = 0, modifiedAt = null)))
    }
}
