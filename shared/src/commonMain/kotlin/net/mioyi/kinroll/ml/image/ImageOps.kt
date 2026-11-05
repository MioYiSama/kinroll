package net.mioyi.kinroll.ml.image

import net.mioyi.kinroll.platform.RgbaImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageOps {
    /** Area averaging on shrinking axes; pixel-centred bilinear sampling on growing axes. */
    fun resize(src: RgbaImage, w: Int, h: Int): RgbaImage = resample(src, w, h, true)

    internal fun resizeBilinear(src: RgbaImage, w: Int, h: Int): RgbaImage =
        resample(src, w, h, false)

    private class Axis(val starts: IntArray, val weights: Array<FloatArray>)

    private fun axis(source: Int, target: Int, area: Boolean): Axis {
        val starts = IntArray(target)
        val scale = source.toDouble() / target
        val weights = Array(target) { i ->
            if (area && target < source) {
                val left = i * scale
                val right = (i + 1) * scale
                val first = floor(left).toInt()
                val last = min(source, ceil(right).toInt())
                starts[i] = first
                FloatArray(last - first) { j ->
                    ((min(right, (first + j + 1).toDouble()) - max(left, (first + j).toDouble())) / scale).toFloat()
                }
            } else {
                val position = ((i + 0.5) * scale - 0.5).coerceIn(0.0, (source - 1).toDouble())
                val first = floor(position).toInt()
                starts[i] = first
                if (first == source - 1) floatArrayOf(1f)
                else floatArrayOf((1.0 - (position - first)).toFloat(), (position - first).toFloat())
            }
        }
        return Axis(starts, weights)
    }

    private fun resample(src: RgbaImage, w: Int, h: Int, area: Boolean): RgbaImage {
        validate(src)
        require(w > 0 && h > 0)
        if (w == src.width && h == src.height) return src
        val xs = axis(src.width, w, area)
        val ys = axis(src.height, h, area)
        val result = ByteArray(w * h * 4)
        // Separable sampling avoids a quadratic kernel when both axes shrink heavily.
        val horizontal = FloatArray(w * src.height * 4)
        for (y in 0 until src.height) for (x in 0 until w) {
            val weights = xs.weights[x]
            val destination = (y * w + x) * 4
            for (channel in 0..3) {
                var value = 0f
                for (j in weights.indices) {
                    value += (src.pixels[(y * src.width + xs.starts[x] + j) * 4 + channel].toInt() and 255) * weights[j]
                }
                horizontal[destination + channel] = value
            }
        }
        for (y in 0 until h) for (x in 0 until w) for (channel in 0..3) {
            var value = 0f
            val weights = ys.weights[y]
            for (j in weights.indices) value += horizontal[((ys.starts[y] + j) * w + x) * 4 + channel] * weights[j]
            result[(y * w + x) * 4 + channel] = value.roundToInt().coerceIn(0, 255).toByte()
        }
        return RgbaImage(w, h, result)
    }

    fun toGray(src: RgbaImage): FloatArray {
        validate(src)
        return FloatArray(src.width * src.height) { i ->
            val p = i * 4
            0.299f * (src.pixels[p].toInt() and 255) +
                0.587f * (src.pixels[p + 1].toInt() and 255) +
                0.114f * (src.pixels[p + 2].toInt() and 255)
        }
    }

    /** The quad's top-left, top-right and bottom-left edges define the affine transform. */
    fun cropRotatedRect(src: RgbaImage, quad: FloatArray, outW: Int, outH: Int): RgbaImage {
        validate(src)
        require(quad.size == 8 && quad.all { it.isFinite() })
        require(outW > 0 && outH > 0)
        val output = ByteArray(outW * outH * 4)
        for (y in 0 until outH) for (x in 0 until outW) {
            val u = (x + 0.5f) / outW
            val v = (y + 0.5f) / outH
            val sx = (quad[0] + u * (quad[2] - quad[0]) + v * (quad[6] - quad[0]) - 0.5f)
                .coerceIn(0f, (src.width - 1).toFloat())
            val sy = (quad[1] + u * (quad[3] - quad[1]) + v * (quad[7] - quad[1]) - 0.5f)
                .coerceIn(0f, (src.height - 1).toFloat())
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val x1 = min(x0 + 1, src.width - 1)
            val y1 = min(y0 + 1, src.height - 1)
            val dx = sx - x0
            val dy = sy - y0
            for (c in 0..3) {
                fun pixel(px: Int, py: Int) = (src.pixels[(py * src.width + px) * 4 + c].toInt() and 255).toFloat()
                val top = pixel(x0, y0) * (1f - dx) + pixel(x1, y0) * dx
                val bottom = pixel(x0, y1) * (1f - dx) + pixel(x1, y1) * dx
                output[(y * outW + x) * 4 + c] = (top * (1f - dy) + bottom * dy).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return RgbaImage(outW, outH, output)
    }

    private fun validate(src: RgbaImage) {
        require(src.width > 0 && src.height > 0 && src.pixels.size.toLong() == src.width.toLong() * src.height * 4)
    }
}
