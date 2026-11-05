package net.mioyi.kinroll.ui.components

import net.mioyi.kinroll.data.BadShotReason
import net.mioyi.kinroll.ml.clip.PhotoCategory
import net.mioyi.kinroll.model.DownloadSource
import net.mioyi.kinroll.model.ModelGroup
import net.mioyi.kinroll.platform.PhotoKind
import net.mioyi.kinroll.post.PostStyle
import net.mioyi.kinroll.resources.Res
import net.mioyi.kinroll.resources.*
import org.jetbrains.compose.resources.StringResource

fun PhotoCategory.label(): StringResource = when (this) {
    PhotoCategory.PEOPLE -> Res.string.category_people
    PhotoCategory.CHILD -> Res.string.category_child
    PhotoCategory.PET -> Res.string.category_pet
    PhotoCategory.FOOD -> Res.string.category_food
    PhotoCategory.LANDSCAPE -> Res.string.category_landscape
    PhotoCategory.CITY -> Res.string.category_city
    PhotoCategory.NIGHT -> Res.string.category_night
    PhotoCategory.PLANT -> Res.string.category_plant
    PhotoCategory.VEHICLE -> Res.string.category_vehicle
    PhotoCategory.DOCUMENT -> Res.string.category_document
    PhotoCategory.RECEIPT -> Res.string.category_receipt
    PhotoCategory.ART -> Res.string.category_art
    PhotoCategory.OBJECT -> Res.string.category_object
    PhotoCategory.OTHER -> Res.string.category_other
}

fun categoryOf(name: String?): PhotoCategory? = name?.let { n -> PhotoCategory.entries.firstOrNull { it.name == n } }

fun kindOf(name: String): PhotoKind = PhotoKind.entries.firstOrNull { it.name == name } ?: PhotoKind.UNKNOWN

fun PhotoKind.label(): StringResource = when (this) {
    PhotoKind.CAMERA -> Res.string.kind_camera
    PhotoKind.SCREENSHOT -> Res.string.kind_screenshot
    PhotoKind.SAVED -> Res.string.kind_saved
    PhotoKind.UNKNOWN -> Res.string.kind_unknown
}

/** Most telling reason first: a photo that is accidental is usually also dark or blurry. */
fun badShotLabel(reasons: Int): StringResource? = when {
    reasons and BadShotReason.ACCIDENTAL != 0 -> Res.string.reason_accidental
    reasons and BadShotReason.BLUR != 0 -> Res.string.reason_blur
    reasons and BadShotReason.DARK != 0 -> Res.string.reason_dark
    reasons and BadShotReason.OVEREXPOSED != 0 -> Res.string.reason_overexposed
    else -> null
}

fun PostStyle.label(): StringResource = when (this) {
    PostStyle.XIAOHONGSHU -> Res.string.style_xiaohongshu
    PostStyle.MOMENTS -> Res.string.style_moments
    PostStyle.WEIBO -> Res.string.style_weibo
    PostStyle.INSTAGRAM -> Res.string.style_instagram
}

fun DownloadSource.label(): StringResource = when (this) {
    DownloadSource.HUGGING_FACE -> Res.string.source_hugging_face
    DownloadSource.HF_MIRROR -> Res.string.source_hf_mirror
    DownloadSource.MODELSCOPE -> Res.string.source_modelscope
}

fun DownloadSource.description(): StringResource = when (this) {
    DownloadSource.HUGGING_FACE -> Res.string.source_hugging_face_desc
    DownloadSource.HF_MIRROR -> Res.string.source_hf_mirror_desc
    DownloadSource.MODELSCOPE -> Res.string.source_modelscope_desc
}

fun ModelGroup.label(): StringResource = when (this) {
    ModelGroup.CLIP -> Res.string.model_group_clip
    ModelGroup.OCR -> Res.string.model_group_ocr
    ModelGroup.LLM -> Res.string.model_group_llm
}

fun ModelGroup.description(): StringResource = when (this) {
    ModelGroup.CLIP -> Res.string.model_group_clip_desc
    ModelGroup.OCR -> Res.string.model_group_ocr_desc
    ModelGroup.LLM -> Res.string.model_group_llm_desc
}
