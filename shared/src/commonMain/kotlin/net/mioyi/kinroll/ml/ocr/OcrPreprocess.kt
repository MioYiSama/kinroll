package net.mioyi.kinroll.ml.ocr

import net.mioyi.kinroll.ml.image.ImageOps
import net.mioyi.kinroll.platform.RgbaImage
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class OcrTensor(val width: Int, val height: Int, val data: FloatArray) {
    val shape: LongArray get() = longArrayOf(1, 3, height.toLong(), width.toLong())
}

internal object OcrPreprocess {
    private val means = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val stds = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun detection(image: RgbaImage): OcrTensor {
        val scale = min(1.0, 960.0 / max(image.width, image.height))
        val width = max(32, (image.width * scale / 32).roundToInt() * 32)
        val height = max(32, (image.height * scale / 32).roundToInt() * 32)
        val resized = ImageOps.resize(image, width, height)
        val plane = width * height
        val data = FloatArray(3 * plane)
        for (c in 0..2) for (i in 0 until plane) {
            val value = resized.pixels[i * 4 + 2 - c].toInt() and 255
            data[c * plane + i] = (value / 255f - means[c]) / stds[c]
        }
        return OcrTensor(width, height, data)
    }

    fun recognition(image: RgbaImage, box: FloatArray): OcrTensor {
        require(box.size == 8)
        val width = max(1, hypot((box[2] - box[0]).toDouble(), (box[3] - box[1]).toDouble()).roundToInt())
        val height = max(1, hypot((box[6] - box[0]).toDouble(), (box[7] - box[1]).toDouble()).roundToInt())
        var crop = ImageOps.cropRotatedRect(image, box, width, height)
        if (height.toDouble() / width >= 1.5) crop = rotateCounterclockwise(crop)
        val resizedWidth = min(3200, ceil(48.0 * crop.width / crop.height).toInt()).coerceAtLeast(1)
        val canvasWidth = max(320, resizedWidth)
        val resized = ImageOps.resize(crop, resizedWidth, 48)
        val plane = canvasWidth * 48
        // Padding must be zero in normalized space, not a normalized black pixel.
        val data = FloatArray(3 * plane)
        for (c in 0..2) for (y in 0 until 48) for (x in 0 until resizedWidth) {
            val value = resized.pixels[(y * resizedWidth + x) * 4 + 2 - c].toInt() and 255
            data[c * plane + y * canvasWidth + x] = value / 127.5f - 1f
        }
        return OcrTensor(canvasWidth, 48, data)
    }

    private fun rotateCounterclockwise(image: RgbaImage): RgbaImage {
        val pixels = ByteArray(image.pixels.size)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val dest = ((image.width - 1 - x) * image.height + y) * 4
            image.pixels.copyInto(pixels, dest, (y * image.width + x) * 4, (y * image.width + x + 1) * 4)
        }
        return RgbaImage(image.height, image.width, pixels)
    }
}
