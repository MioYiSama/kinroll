package net.mioyi.kinroll.ml.ocr

import net.mioyi.kinroll.platform.RgbaImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrPreprocessTest {
    private fun image(width: Int, height: Int): RgbaImage = RgbaImage(width, height, ByteArray(width * height * 4).also {
        for (i in 0 until width * height) {
            it[i * 4] = 255.toByte()
            it[i * 4 + 1] = 127
            it[i * 4 + 2] = 0
            it[i * 4 + 3] = 255.toByte()
        }
    })

    @Test fun detectionUsesBgrAndMultipleOf32() {
        val tensor = OcrPreprocess.detection(image(1000, 500))
        assertEquals(960, tensor.width)
        assertEquals(480, tensor.height)
        assertTrue(kotlin.math.abs(tensor.data[0] - -0.485f / 0.229f) < 1e-5)
        assertTrue(kotlin.math.abs(tensor.data[tensor.width * tensor.height * 2] - (1f - 0.406f) / 0.225f) < 1e-5)
    }

    @Test fun recognitionPadsInNormalizedSpace() {
        val tensor = OcrPreprocess.recognition(image(40, 20), floatArrayOf(0f, 0f, 39f, 0f, 39f, 19f, 0f, 19f))
        assertEquals(48, tensor.height)
        assertEquals(320, tensor.width)
        assertEquals(-1f, tensor.data[0])
        assertEquals(1f, tensor.data[320 * 48 * 2])
        assertEquals(0f, tensor.data[319])
    }

    @Test fun verticalCropsRotate() {
        val tensor = OcrPreprocess.recognition(image(40, 200), floatArrayOf(0f, 0f, 20f, 0f, 20f, 180f, 0f, 180f))
        assertEquals(432, tensor.width)
    }

    @Test fun resultTextAndJsonEscapeText() {
        val box = floatArrayOf(0f, 1f, 2f, 1f, 2f, 3f, 0f, 3f)
        val result = OcrResult(listOf(OcrLine("猫\"\n", 0.9f, box), OcrLine("相知", 0.8f, box)))
        assertEquals("猫\"\n\n相知", result.text)
        val json = Json.parseToJsonElement(result.toJson()).jsonArray
        assertEquals("猫\"\n", json[0].jsonObject.getValue("t").jsonPrimitive.content)
        assertEquals(8, json[0].jsonObject.getValue("b").jsonArray.size)
        assertEquals("[]", OcrResult(emptyList()).toJson())
    }
}
