package net.mioyi.kinroll.ml.clip

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipTokenizerTest {
    private fun tokenizer(arrayMerges: Boolean = false): ClipTokenizer {
        val vocabulary = mapOf(
            "<|startoftext|>" to 100, "<|endoftext|>" to 101,
            "h" to 1, "e" to 2, "l" to 3, "o</w>" to 4,
            "he" to 5, "ll" to 6, "hell" to 7, "hello</w>" to 8,
            "a</w>" to 9, "1</w>" to 10, "2</w>" to 11,
            "'" to 12, "s</w>" to 13, "'s</w>" to 14, "!</w>" to 15,
        )
        val merges = listOf("h" to "e", "l" to "l", "he" to "ll", "hell" to "o</w>", "'" to "s</w>")
        return ClipTokenizer.fromJson(JsonObject(mapOf("model" to JsonObject(mapOf(
            "vocab" to JsonObject(vocabulary.mapValues { JsonPrimitive(it.value) }),
            "merges" to JsonArray(merges.map { (a, b) ->
                if (arrayMerges) JsonArray(listOf(JsonPrimitive(a), JsonPrimitive(b))) else JsonPrimitive("$a $b")
            }),
        )))).toString())
    }

    @Test fun mergesBothFormatsAndWordSuffix() {
        for (arrayMerges in listOf(false, true)) {
            val ids = tokenizer(arrayMerges).encode("Hello hello's!")
            assertContentEquals(longArrayOf(100, 8, 8, 14, 15, 101), ids.copyOf(6))
            assertEquals(77, ids.size)
            assertTrue(ids.drop(6).all { it == 0L })
        }
    }

    @Test fun lowercasesCollapsesWhitespaceAndSeparatesDigits() {
        val ids = tokenizer().encode("  HELLO\t\n a 12  ")
        assertContentEquals(longArrayOf(100, 8, 9, 10, 11, 101), ids.copyOf(6))
    }

    @Test fun emptyTextStillHasSpecialTokens() {
        val ids = tokenizer().encode(" \n\t")
        assertContentEquals(longArrayOf(100, 101), ids.copyOf(2))
        assertTrue(ids.drop(2).all { it == 0L })
    }

    @Test fun truncationReservesEndToken() {
        val ids = tokenizer().encode(List(100) { "a" }.joinToString(" "))
        assertEquals(100L, ids.first())
        assertTrue(ids.slice(1..75).all { it == 9L })
        assertEquals(101L, ids.last())
    }

    @Test fun gpt2Utf8ByteMapping() {
        // 猫 UTF-8 = E7 8C AB; GPT-2 maps 8C to U+012E and appends the suffix only to AB.
        val tokenizer = ClipTokenizer.fromJson("""{"model":{"vocab":{"<|startoftext|>":100,"<|endoftext|>":101,"ç":1,"Į":2,"«</w>":3},"merges":[]}}""")
        assertContentEquals(longArrayOf(100, 1, 2, 3, 101), tokenizer.encode("猫").copyOf(5))
    }

    @Test fun portableUnicodeCategoriesAndWhitespace() {
        // Regression: Native Regex's negated Unicode category class also matched spaces.
        assertEquals(listOf("café猫", "١", "２", "!"), ClipTextPieces.split("café猫 ١２!").toList())
        assertEquals(listOf("𐐀a", "𝟙", "🐱!"), ClipTextPieces.split("𐐀a 𝟙 🐱!").toList())
        assertEquals(listOf("Ⅳ", "²", "½"), ClipTextPieces.split("Ⅳ²½").toList())
        assertEquals("hello world", ClipTextPieces.normalize("\u00a0HELLO\u2003\tworld\u0085"))
    }
}
