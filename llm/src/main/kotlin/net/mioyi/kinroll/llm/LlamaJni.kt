package net.mioyi.kinroll.llm

object LlamaJni {
    init { System.loadLibrary("kinroll_llm") }

    external fun load(path: String, nCtx: Int, nThreads: Int): Long
    external fun lastError(): String
    external fun begin(
        handle: Long,
        promptUtf8: ByteArray,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
        seed: Int,
    ): Boolean
    external fun next(handle: Long): ByteArray?
    external fun free(handle: Long)
}
