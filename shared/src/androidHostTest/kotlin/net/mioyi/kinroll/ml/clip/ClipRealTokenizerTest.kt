package net.mioyi.kinroll.ml.clip

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Set KINROLL_CLIP_TOKENIZER to Xenova/mobileclip_s2's downloaded tokenizer.json. */
class ClipRealTokenizerTest {
    @Test fun matchesHuggingFaceTokenizers() {
        val path = System.getenv("KINROLL_CLIP_TOKENIZER") ?: return
        val tokenizer = ClipTokenizer.fromJson(File(path).readText())
        // Generated with Hugging Face tokenizers 0.23.3 from the same tokenizer.json.
        val fixtures = listOf(
            "a photo of people" to longArrayOf(49406, 320, 1125, 539, 1047, 49407),
            "a photo of a pet cat or dog" to longArrayOf(49406, 320, 1125, 539, 320, 3703, 2368, 541, 1929, 49407),
            "a blurry photo" to longArrayOf(49406, 320, 21977, 1125, 49407),
            "We're looking at café 猫 123!" to longArrayOf(49406, 649, 982, 1312, 536, 15304, 163, 234, 360, 272, 273, 274, 256, 49407),
            "  HELLO\tworld!  " to longArrayOf(49406, 3306, 1002, 256, 49407),
        )
        for ((text, expected) in fixtures) {
            val ids = tokenizer.encode(text)
            assertContentEquals(expected, ids.copyOf(expected.size), text)
            assertTrue(ids.drop(expected.size).all { it == 0L }, text)
        }
    }
}
