package net.mioyi.kinroll.llm

import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mioyi.kinroll.model.ModelManager
import net.mioyi.kinroll.model.ModelManifest
import net.mioyi.kinroll.platform.LlamaNative
import net.mioyi.kinroll.platform.SamplingParams

class LlmService(
    private val native: LlamaNative,
    private val modelManager: ModelManager,
    private val cpuThreads: Int,
) {
    private val mutex = Mutex()
    private val mutableLoading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()
    private var loaded = false

    fun generate(system: String, user: String, maxTokens: Int, params: SamplingParams): Flow<String> = flow {
        require(maxTokens > 0)
        mutex.withLock {
            coroutineContext.ensureActive()
            if (!loaded) {
                check(modelManager.isReady(ModelManifest.file("llm"))) { "LLM model is not ready" }
                mutableLoading.value = true
                try {
                    val error = native.load(modelManager.localPath("llm"), 4096, cpuThreads)
                    check(error == null) { error ?: "LLM model load failed" }
                    loaded = true
                } finally {
                    mutableLoading.value = false
                }
            }
            coroutineContext.ensureActive()
            val error = native.begin(QwenPrompt.format(system, user), params)
            check(error == null) { error ?: "LLM prompt decoding failed" }
            val utf8 = Utf8Accumulator()
            val filter = LeadingThinkingFilter()
            for (token in 0 until maxTokens) {
                coroutineContext.ensureActive()
                val bytes = native.next() ?: break
                coroutineContext.ensureActive()
                val text = filter.push(utf8.push(bytes))
                if (text.isNotEmpty()) emit(text)
            }
            val tail = filter.push(utf8.flush()) + filter.finish()
            if (tail.isNotEmpty()) emit(tail)
        }
    }.flowOn(Dispatchers.Default)

    suspend fun translateToEnglish(query: String): String? {
        if (!modelManager.isReady(ModelManifest.file("llm"))) return null
        return try {
            val result = StringBuilder()
            generate(
                "Translate the user's photo search query into concise English for an image search engine. Output only the translation.",
                query,
                32,
                SamplingParams(0f, 1f, 1, 0f, 0),
            ).collect { result.append(it) }
            result.toString().trim().takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            println("LLM translation failed: ${error.message}")
            null
        }
    }

    companion object {
        fun postParams(): SamplingParams = SamplingParams(0.7f, 0.8f, 20, 1.5f, Random.nextInt())
    }
}

/** Detects the opening tag across token boundaries without buffering the generated answer. */
internal class LeadingThinkingFilter {
    private var state = 0 // opening tag, reasoning, leading whitespace, answer
    private var openingMatched = 0
    private var closingMatched = 0

    fun push(text: String): String {
        if (state == 3) return text
        for (index in text.indices) {
            val char = text[index]
            when (state) {
                0 -> {
                    if (char != "<think>"[openingMatched]) {
                        state = 3
                        return if (openingMatched == 0) text.substring(index)
                            else "<think>".substring(0, openingMatched) + text.substring(index)
                    }
                    openingMatched++
                    if (openingMatched == 7) state = 1
                }
                1 -> {
                    closingMatched = if (char == "</think>"[closingMatched]) closingMatched + 1
                        else if (char == '<') 1 else 0
                    if (closingMatched == 8) state = 2
                }
                2 -> if (!char.isWhitespace()) {
                    state = 3
                    return text.substring(index)
                }
            }
        }
        return ""
    }

    fun finish(): String {
        if (state != 0) return ""
        val text = "<think>".substring(0, openingMatched)
        openingMatched = 0
        return text
    }
}
