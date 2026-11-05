package net.mioyi.kinroll.ml.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CtcDecoderTest {
    @Test fun blankAndAdjacentDuplicates() {
        val logits = floatArrayOf(
            0.9f, 0.05f, 0.05f,
            0.05f, 0.9f, 0.05f,
            0.1f, 0.8f, 0.1f,
            0.9f, 0.05f, 0.05f,
            0.1f, 0.2f, 0.7f,
        )
        val result = CtcDecoder.decode(logits, 5, 3, listOf("a", "b"))
        assertEquals("ab", result.text)
        assertTrue(kotlin.math.abs(result.score - 0.8f) < 1e-6f)
    }

    @Test fun blankSeparatesIdenticalCharacters() {
        val result = CtcDecoder.decode(floatArrayOf(0f, 1f, 1f, 0f, 0f, 1f), 3, 2, listOf("猫"))
        assertEquals("猫猫", result.text)
        assertEquals(1f, result.score)
    }

    @Test fun allBlankHasZeroConfidence() {
        assertEquals(DecodedText("", 0f), CtcDecoder.decode(floatArrayOf(1f, 0f), 1, 2, listOf("a")))
    }
}
