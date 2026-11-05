package net.mioyi.kinroll.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Utf8AccumulatorTest {
    @Test
    fun splitChineseAndEmojiIntoSingleBytes() {
        val accumulator = Utf8Accumulator()
        val output = StringBuilder()
        for (byte in "猫🐱".encodeToByteArray()) {
            val delta = accumulator.push(byteArrayOf(byte))
            assertFalse(delta.contains('\uFFFD'))
            output.append(delta)
        }
        output.append(accumulator.flush())
        assertEquals("猫🐱", output.toString())
    }

    @Test
    fun everySplitPreservesMixedText() {
        val text = "A猫🐱 café\n끝"
        val bytes = text.encodeToByteArray()
        for (split in 0..bytes.size) {
            val accumulator = Utf8Accumulator()
            assertEquals(text, accumulator.push(bytes.copyOfRange(0, split)) +
                accumulator.push(bytes.copyOfRange(split, bytes.size)) + accumulator.flush())
        }
    }

    @Test
    fun flushReplacesTruncatedCodePointAndResets() {
        val accumulator = Utf8Accumulator()
        assertEquals("", accumulator.push(byteArrayOf(0xf0.toByte(), 0x9f.toByte())))
        assertEquals("\uFFFD", accumulator.flush())
        assertEquals("", accumulator.flush())
        assertEquals("ok", accumulator.push("ok".encodeToByteArray()))
    }

    @Test
    fun thinkingTagsCanSpanDeltas() {
        val filter = LeadingThinkingFilter()
        val output = StringBuilder()
        "<think>reasoning 猫</think>\n\nA cat".forEach { output.append(filter.push(it.toString())) }
        output.append(filter.finish())
        assertEquals("A cat", output.toString())
    }

    @Test
    fun ordinaryAnswersAndPartialOpeningTagsArePreserved() {
        val filter = LeadingThinkingFilter()
        assertEquals("<title>cat", filter.push("<ti") + filter.push("tle>cat") + filter.finish())
        val partial = LeadingThinkingFilter()
        assertEquals("<thi", partial.push("<thi") + partial.finish())
    }
}
