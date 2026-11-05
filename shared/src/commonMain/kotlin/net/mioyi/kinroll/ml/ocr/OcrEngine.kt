package net.mioyi.kinroll.ml.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.mioyi.kinroll.model.ModelManager
import net.mioyi.kinroll.platform.InferenceRuntime
import net.mioyi.kinroll.platform.InferenceSession
import net.mioyi.kinroll.platform.RgbaImage
import net.mioyi.kinroll.platform.TensorInput

/** Recognized line with confidence and original-image tl,tr,br,bl coordinates (x,y pairs). */
class OcrLine(val text: String, val score: Float, val box: FloatArray)

class OcrResult(val lines: List<OcrLine>) {
    val text: String get() = lines.joinToString("\n") { it.text }
    fun toJson(): String = JsonArray(lines.map { line ->
        JsonObject(mapOf(
            "t" to JsonPrimitive(line.text),
            "s" to JsonPrimitive(line.score),
            "b" to JsonArray(line.box.map { JsonPrimitive(it) }),
        ))
    }).toString()
}

/**
 * Offline PP-OCRv6 small detector/recognizer. Downloads are managed externally; sessions and
 * the YAML dictionary are opened on first recognition, then serialized by [mutex].
 *
 * Both models accept BGR NCHW tensors. The official recognition dictionary has 18,708
 * entries: appending space plus CTC blank matches the model's 18,710 output classes.
 */
class OcrEngine(private val runtime: InferenceRuntime, private val modelManager: ModelManager) {
    private class Sessions(val detection: InferenceSession, val recognition: InferenceSession, private val dictionary: List<String>) {
        private var preparedDictionary: List<String>? = null
        fun dictionaryFor(classCount: Int): List<String> {
            val prepared = preparedDictionary ?: RecDictionary.forClassCount(dictionary, classCount).also { preparedDictionary = it }
            require(prepared.size + 1 == classCount) { "OCR recognition class count changed" }
            return prepared
        }
    }
    private val mutex = Mutex()
    private var sessions: Sessions? = null
    private val postProcess = DbPostProcess()

    private fun sessions(): Sessions {
        sessions?.let { return it }
        val dictionary = SystemFileSystem.source(Path(modelManager.localPath("ocr_rec_config"))).buffered().use {
            RecDictionary.parse(it.readString())
        }
        val detection = runtime.open(modelManager.localPath("ocr_det"))
        val recognition = try { runtime.open(modelManager.localPath("ocr_rec")) } catch (failure: Throwable) {
            detection.close()
            throw failure
        }
        return Sessions(detection, recognition, dictionary).also { sessions = it }
    }

    suspend fun recognize(img: RgbaImage): OcrResult = withContext(Dispatchers.Default) {
        require(img.width > 0 && img.height > 0 && img.pixels.size.toLong() == img.width.toLong() * img.height * 4)
        mutex.withLock {
            val active = sessions()
            val detection = OcrPreprocess.detection(img)
            val detOutput = active.detection.run(
                listOf(TensorInput(active.detection.inputNames.single(), detection.shape, floats = detection.data)),
                listOf(active.detection.outputNames.first()),
            ).single()
            require(detOutput.shape.size == 4 && detOutput.shape[0] == 1L && detOutput.shape[1] == 1L) {
                "Expected OCR detection output [1,1,H,W]"
            }
            val boxes = postProcess.boxes(detOutput.data, detOutput.shape[3].toInt(), detOutput.shape[2].toInt(), img.width, img.height)
            val lines = ArrayList<OcrLine>(boxes.size)
            for (box in boxes) {
                ensureActive()
                val recognition = OcrPreprocess.recognition(img, box)
                val output = active.recognition.run(
                    listOf(TensorInput(active.recognition.inputNames.single(), recognition.shape, floats = recognition.data)),
                    listOf(active.recognition.outputNames.first()),
                ).single()
                require(output.shape.size == 3 && output.shape[0] == 1L) { "Expected OCR recognition output [1,T,C]" }
                val classes = output.shape[2].toInt()
                val chars = active.dictionaryFor(classes)
                val decoded = CtcDecoder.decode(output.data, output.shape[1].toInt(), classes, chars)
                if (decoded.text.isNotEmpty() && decoded.score >= 0.5f) lines.add(OcrLine(decoded.text, decoded.score, box))
            }
            OcrResult(lines)
        }
    }
}
