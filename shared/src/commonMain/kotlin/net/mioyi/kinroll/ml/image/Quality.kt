package net.mioyi.kinroll.ml.image

import net.mioyi.kinroll.platform.RgbaImage
import kotlin.math.max
import kotlin.math.roundToInt

data class QualityResult(val blurScore: Float, val brightness: Float, val dHash: Long)

object Quality {
    fun analyze(img: RgbaImage): QualityResult {
        val scale = 512.0 / max(img.width, img.height)
        val sample = ImageOps.resize(img, max(1, (img.width * scale).roundToInt()), max(1, (img.height * scale).roundToInt()))
        val gray = ImageOps.toGray(sample)
        var brightness = 0.0
        for (value in gray) brightness += value
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in 1 until sample.height - 1) for (x in 1 until sample.width - 1) {
            val i = y * sample.width + x
            val laplacian = (gray[i - 1] + gray[i + 1] + gray[i - sample.width] + gray[i + sample.width] - 4f * gray[i]).toDouble()
            sum += laplacian
            sumSquares += laplacian * laplacian
            count++
        }
        val variance = if (count == 0) 0f else max(0.0, sumSquares / count - (sum / count) * (sum / count)).toFloat()
        val hashGray = ImageOps.toGray(ImageOps.resize(img, 9, 8))
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            if (hashGray[y * 9 + x] > hashGray[y * 9 + x + 1]) hash = hash or (1L shl (y * 8 + x))
        }
        return QualityResult(variance, (brightness / gray.size / 255.0).toFloat(), hash)
    }

    fun hamming(a: Long, b: Long): Int = (a xor b).countOneBits()
}
