package net.mioyi.kinroll.ml.clip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import net.mioyi.kinroll.ml.image.ImageOps
import net.mioyi.kinroll.model.ModelManager
import net.mioyi.kinroll.platform.InferenceRuntime
import net.mioyi.kinroll.platform.InferenceSession
import net.mioyi.kinroll.platform.RgbaImage
import net.mioyi.kinroll.platform.TensorInput
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

class ClipEngine(private val runtime: InferenceRuntime, private val modelManager: ModelManager) {
    private val visionMutex = Mutex()
    private val textMutex = Mutex()
    private var visionSession: InferenceSession? = null
    private var textSession: InferenceSession? = null
    private var tokenizer: ClipTokenizer? = null
    private val textCache = mutableMapOf<String, FloatArray>()

    suspend fun imageEmbedding(img: RgbaImage): FloatArray = withContext(Dispatchers.Default) {
        visionMutex.withLock {
            val session = visionSession ?: runtime.open(modelManager.localPath("clip_vision")).also { visionSession = it }
            val scale = 256.0 / minOf(img.width, img.height)
            val resized = ImageOps.resizeBilinear(img, max(256, (img.width * scale).toInt()), max(256, (img.height * scale).toInt()))
            val left = (resized.width - 256) / 2
            val top = (resized.height - 256) / 2
            val plane = 256 * 256
            val input = FloatArray(plane * 3)
            for (y in 0 until 256) for (x in 0 until 256) {
                val source = ((top + y) * resized.width + left + x) * 4
                val target = y * 256 + x
                for (c in 0..2) input[c * plane + target] = (resized.pixels[source + c].toInt() and 255) / 255f
            }
            val output = if ("image_embeds" in session.outputNames) "image_embeds" else session.outputNames.first()
            normalized(session.run(listOf(TensorInput(session.inputNames.first(), longArrayOf(1, 3, 256, 256), floats = input)), listOf(output)).single().data)
        }
    }

    suspend fun textEmbedding(prompt: String): FloatArray = withContext(Dispatchers.Default) {
        textMutex.withLock {
            textCache[prompt]?.let { return@withLock it.copyOf() }
            val codec = tokenizer ?: ClipTokenizer.fromJson(
                SystemFileSystem.source(Path(modelManager.localPath("clip_tokenizer"))).buffered().use { it.readString() },
            ).also { tokenizer = it }
            val session = textSession ?: runtime.open(modelManager.localPath("clip_text")).also { textSession = it }
            val ids = codec.encode(prompt)
            val inputs = ArrayList<TensorInput>(2)
            inputs.add(TensorInput("input_ids", longArrayOf(1, 77), longs = ids))
            if ("attention_mask" in session.inputNames) {
                val end = ids.indexOf(codec.endToken)
                inputs.add(TensorInput("attention_mask", longArrayOf(1, 77), longs = LongArray(77) { if (it <= end) 1L else 0L }))
            }
            val output = if ("text_embeds" in session.outputNames) "text_embeds" else session.outputNames.first()
            val embedding = normalized(session.run(inputs, listOf(output)).single().data)
            textCache[prompt] = embedding
            embedding.copyOf()
        }
    }

    suspend fun classify(imageEmbedding: FloatArray): CategoryResult {
        val categories = PhotoCategory.entries.filter { it != PhotoCategory.OTHER }
        val probabilities = probabilities(imageEmbedding, categories.map { it.prompt })
        var best = 0
        for (i in 1 until probabilities.size) if (probabilities[i] > probabilities[best]) best = i
        val score = probabilities[best]
        return CategoryResult(if (score < 0.30f) PhotoCategory.OTHER else categories[best], score)
    }

    suspend fun accidentalProbability(imageEmbedding: FloatArray): Float {
        val probabilities = probabilities(imageEmbedding, accidentalPrompts)
        return probabilities[0] + probabilities[1] + probabilities[2] + probabilities[3]
    }

    private suspend fun probabilities(image: FloatArray, prompts: List<String>): FloatArray {
        require(image.size == 512)
        var imageNorm = 0.0
        for (v in image) imageNorm += v.toDouble() * v
        require(imageNorm.isFinite() && imageNorm > 0.0)
        imageNorm = sqrt(imageNorm)
        val logits = DoubleArray(prompts.size)
        for (i in prompts.indices) {
            val text = textEmbedding(prompts[i])
            var cosine = 0.0
            for (j in image.indices) cosine += image[j].toDouble() * text[j]
            logits[i] = 100.0 * cosine / imageNorm
        }
        val maximum = logits.maxOrNull()!!
        var total = 0.0
        for (i in logits.indices) {
            logits[i] = exp(logits[i] - maximum)
            total += logits[i]
        }
        return FloatArray(logits.size) { (logits[it] / total).toFloat() }
    }

    private fun normalized(values: FloatArray): FloatArray {
        require(values.size == 512) { "Expected a 512-dimensional CLIP embedding, got ${values.size}" }
        var norm = 0.0
        for (v in values) norm += v.toDouble() * v
        require(norm.isFinite() && norm > 0.0) { "Invalid CLIP embedding norm" }
        val inverse = (1.0 / sqrt(norm)).toFloat()
        for (i in values.indices) values[i] *= inverse
        return values
    }
}
