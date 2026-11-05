package net.mioyi.kinroll.ml.image

import net.mioyi.kinroll.platform.RgbaImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityTest {
    private fun image(w: Int, h: Int, value: (Int, Int, Int) -> Int): RgbaImage =
        RgbaImage(w, h, ByteArray(w * h * 4) { i ->
            if (i % 4 == 3) 255.toByte() else value((i / 4) % w, i / (w * 4), i % 4).toByte()
        })

    @Test fun solidImageHasZeroLaplacianVariance() {
        val result = Quality.analyze(image(1024, 768) { _, _, _ -> 128 })
        assertEquals(0f, result.blurScore)
        assertEquals(128f / 255f, result.brightness, 0.00001f)
        assertEquals(0L, result.dHash)
    }

    @Test fun hashIsStableAcrossResizing() {
        val source = image(320, 240) { x, y, _ ->
            ((x * 0.5 + y * 0.3) % 256).toInt()
        }
        val scaled = ImageOps.resize(source, 160, 120)
        assertTrue(Quality.hamming(Quality.analyze(source).dHash, Quality.analyze(scaled).dHash) <= 2)
    }

    @Test fun sharpEdgesHaveHigherVarianceThanFlatPixels() {
        val flat = Quality.analyze(image(512, 512) { _, _, _ -> 128 })
        val sharp = Quality.analyze(image(512, 512) { x, y, _ -> if ((x / 8 + y / 8) % 2 == 0) 0 else 255 })
        assertTrue(sharp.blurScore > flat.blurScore)
    }

    @Test fun hashDirectionAndHammingCoverAllBits() {
        val decreasing = image(90, 80) { x, _, _ -> 255 - x * 2 }
        val increasing = image(90, 80) { x, _, _ -> x * 2 }
        assertEquals(-1L, Quality.analyze(decreasing).dHash)
        assertEquals(0L, Quality.analyze(increasing).dHash)
        assertEquals(64, Quality.hamming(-1L, 0L))
        assertEquals(0, Quality.hamming(Long.MIN_VALUE, Long.MIN_VALUE))
    }

    @Test fun downscaleAveragesAreaInsteadOfSkippingPixels() {
        val src = image(4, 1) { x, _, _ -> if (x % 2 == 0) 0 else 200 }
        val result = ImageOps.resize(src, 1, 1)
        assertEquals(100, result.pixels[0].toInt() and 255)
        assertEquals(255, result.pixels[3].toInt() and 255)
        val fractional = ImageOps.resize(image(3, 1) { x, _, _ -> x * 100 }, 2, 1)
        assertEquals(33, fractional.pixels[0].toInt() and 255)
        assertEquals(167, fractional.pixels[4].toInt() and 255)
    }

    @Test fun upscaleIsBilinearAndGrayUsesLuminance() {
        val result = ImageOps.resize(image(2, 1) { x, _, _ -> x * 200 }, 4, 1)
        assertEquals(listOf(0, 50, 150, 200), (0..3).map { result.pixels[it * 4].toInt() and 255 })
        val red = RgbaImage(1, 1, byteArrayOf(-1, 0, 0, -1))
        assertEquals(76.245f, ImageOps.toGray(red)[0], 0.001f)
    }

    @Test fun affineCropIdentityAndRotation() {
        val src = image(3, 2) { x, y, _ -> x + y * 10 }
        val identity = ImageOps.cropRotatedRect(src, floatArrayOf(0f, 0f, 3f, 0f, 3f, 2f, 0f, 2f), 3, 2)
        assertContentEquals(src.pixels, identity.pixels)
        val rotated = ImageOps.cropRotatedRect(src, floatArrayOf(3f, 0f, 3f, 2f, 0f, 2f, 0f, 0f), 2, 3)
        assertEquals(listOf(2, 12, 1, 11, 0, 10), (0..5).map { rotated.pixels[it * 4].toInt() and 255 })
    }
}
