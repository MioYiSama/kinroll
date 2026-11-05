package net.mioyi.kinroll.ml.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecDictionaryTest {
    @Test fun yamlScalarsAndStopAtNextKey() {
        val yaml = "PostProcess:\n  character_dict:\n  - ''''\n  - '\"'\n  - plain\n" +
            "  - \"\\\\\"\n  - \"\\\"\"\n  - \"\\u732b\"\n  name: next\n  - ignored\n"
        assertEquals(listOf("'", "\"", "plain", "\\", "\"", "猫"), RecDictionary.parse(yaml))
    }

    @Test fun preservesQuotedSpacesAndAppendsOptionalSpace() {
        val dictionary = RecDictionary.parse("  character_dict:\n  - ' '\n  - a\n")
        assertEquals(listOf(" ", "a"), dictionary)
        assertEquals(dictionary, RecDictionary.forClassCount(dictionary, 3))
        assertEquals(dictionary + " ", RecDictionary.forClassCount(dictionary, 4))
        assertFailsWith<IllegalArgumentException> { RecDictionary.forClassCount(dictionary, 5) }
    }

    @Test fun rejectsMissingDictionary() {
        assertFailsWith<IllegalStateException> { RecDictionary.parse("name: CTCLabelDecode") }
    }
}
