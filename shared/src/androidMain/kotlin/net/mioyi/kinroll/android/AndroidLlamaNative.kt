package net.mioyi.kinroll.android

import net.mioyi.kinroll.llm.LlamaJni
import net.mioyi.kinroll.platform.LlamaNative
import net.mioyi.kinroll.platform.SamplingParams

/** Calls are serialized by LlmService; this adapter owns the native handle. */
class AndroidLlamaNative : LlamaNative {
    private var handle = 0L

    override fun load(modelPath: String, contextSize: Int, threads: Int): String? {
        unload()
        handle = LlamaJni.load(modelPath, contextSize, threads)
        return if (handle == 0L) LlamaJni.lastError() else null
    }

    override fun begin(prompt: String, params: SamplingParams): String? {
        if (handle == 0L) return "LLM model is not loaded"
        return if (LlamaJni.begin(handle, prompt.encodeToByteArray(), params.temperature,
                params.topP, params.topK, params.presencePenalty, params.seed)) null
            else LlamaJni.lastError()
    }

    override fun next(): ByteArray? = if (handle == 0L) null else LlamaJni.next(handle)

    override fun unload() {
        if (handle != 0L) LlamaJni.free(handle)
        handle = 0L
    }
}
