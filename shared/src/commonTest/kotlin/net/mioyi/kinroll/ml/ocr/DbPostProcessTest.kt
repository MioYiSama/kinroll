package net.mioyi.kinroll.ml.ocr

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbPostProcessTest {
    private fun rectangle(score: Float): FloatArray = FloatArray(64 * 64).also { map ->
        for (y in 24 until 32) for (x in 20 until 40) map[y * 64 + x] = score
    }

    @Test fun expansionAndCornerOrder() {
        val box = DbPostProcess().boxes(rectangle(0.9f), 64, 64, 64, 64).single()
        val distance = 20.0 * 8 * 1.4 / (2 * (20 + 8))
        val width = hypot((box[2] - box[0]).toDouble(), (box[3] - box[1]).toDouble())
        val height = hypot((box[6] - box[0]).toDouble(), (box[7] - box[1]).toDouble())
        assertTrue(abs(width - (20 + 2 * distance)) <= 1)
        assertTrue(abs(height - (8 + 2 * distance)) <= 1)
        assertTrue(box[0] < box[2] && box[1] < box[7])
        assertEquals(box[1], box[3])
    }

    @Test fun lowScoreAndTinyRegionsAreRejected() {
        assertTrue(DbPostProcess().boxes(rectangle(0.4f), 64, 64, 64, 64).isEmpty())
        val tiny = FloatArray(64 * 64).also { it[10 * 64 + 10] = 0.9f }
        assertTrue(DbPostProcess().boxes(tiny, 64, 64, 64, 64).isEmpty())
    }

    @Test fun diagonalPixelsUseEightConnectivity() {
        val map = FloatArray(32 * 32)
        for (y in 4..8) for (x in 4..8) map[y * 32 + x] = 0.9f
        for (y in 9..13) for (x in 9..13) map[y * 32 + x] = 0.9f
        assertEquals(1, DbPostProcess(boxThresh = 0.2f).boxes(map, 32, 32, 32, 32).size)
    }

    @Test fun mappingClampsToImageBoundsAndSortsReadingOrder() {
        val map = rectangle(0.9f)
        for (y in 0..6) for (x in 0..12) map[y * 64 + x] = 0.9f
        val boxes = DbPostProcess().boxes(map, 64, 64, 128, 96)
        assertEquals(2, boxes.size)
        assertEquals(0f, boxes[0][0])
        assertEquals(0f, boxes[0][1])
        assertTrue(boxes[0][1] < boxes[1][1])
        for (box in boxes) for (i in 0..3) {
            assertTrue(box[i * 2] in 0f..127f)
            assertTrue(box[i * 2 + 1] in 0f..95f)
        }
    }
}
