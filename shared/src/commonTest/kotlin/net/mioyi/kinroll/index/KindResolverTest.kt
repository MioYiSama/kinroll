package net.mioyi.kinroll.index

import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoDetails
import net.mioyi.kinroll.platform.PhotoKind
import kotlin.test.Test
import kotlin.test.assertEquals

class KindResolverTest {
    private fun asset(path: String? = null, screenshot: Boolean = false) = PhotoAsset(
        id = "1", uri = "photo://1", fileName = "IMG.jpg", mimeType = "image/jpeg", sizeBytes = 1,
        width = 100, height = 100, orientation = 0, takenAt = null, addedAt = null, modifiedAt = null,
        album = null, relativePath = path, ownerPackage = null, isFavorite = false,
        isScreenshotHint = screenshot, latitude = null, longitude = null,
    )
    private fun details(make: String? = null, model: String? = null) = PhotoDetails(
        make, model, null, null, null, null, null, null, null, null,
    )

    @Test fun screenshotTakesPrecedenceOverSavedPathAndCamera() {
        assertEquals(PhotoKind.SCREENSHOT, KindResolver.resolve(asset("Download/", true), details("Apple")))
    }

    @Test fun savedPathsTakePrecedenceOverCameraMetadata() {
        for (path in listOf("DOWNLOAD/", "Pictures/WeiXin/", "WeChat/", "Tencent/", "QQ/", "Weibo/", "Xiaohongshu/", "Douyin/", "Telegram/", "WhatsApp/")) {
            assertEquals(PhotoKind.SAVED, KindResolver.resolve(asset(path), details("Samsung")), path)
        }
    }

    @Test fun savedPathDoesNotRequireReadableDetails() {
        assertEquals(PhotoKind.SAVED, KindResolver.resolve(asset("Download/Images/"), null))
    }

    @Test fun makeOrModelIdentifiesCamera() {
        assertEquals(PhotoKind.CAMERA, KindResolver.resolve(asset("DCIM/Camera/"), details(make = "Apple")))
        assertEquals(PhotoKind.CAMERA, KindResolver.resolve(asset(), details(model = "Pixel")))
    }

    @Test fun unreadableOriginalIsUnknownButReadableWithoutExifIsSaved() {
        assertEquals(PhotoKind.UNKNOWN, KindResolver.resolve(asset(), null))
        assertEquals(PhotoKind.SAVED, KindResolver.resolve(asset(), details()))
        assertEquals(PhotoKind.SAVED, KindResolver.resolve(asset(), details(make = " ", model = "")))
    }
}
