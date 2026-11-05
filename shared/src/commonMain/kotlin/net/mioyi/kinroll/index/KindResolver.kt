package net.mioyi.kinroll.index

import net.mioyi.kinroll.platform.PhotoAsset
import net.mioyi.kinroll.platform.PhotoDetails
import net.mioyi.kinroll.platform.PhotoKind

object KindResolver {
    private val savedPaths = listOf("weixin", "wechat", "tencent", "qq", "weibo", "xiaohongshu", "douyin", "telegram", "whatsapp")

    fun resolve(asset: PhotoAsset, details: PhotoDetails?): PhotoKind {
        if (asset.isScreenshotHint) return PhotoKind.SCREENSHOT
        val path = asset.relativePath.orEmpty().lowercase()
        if (path.startsWith("download/") || savedPaths.any(path::contains)) return PhotoKind.SAVED
        if (!details?.cameraMake.isNullOrBlank() || !details?.cameraModel.isNullOrBlank()) return PhotoKind.CAMERA
        return if (details == null) PhotoKind.UNKNOWN else PhotoKind.SAVED
    }
}
