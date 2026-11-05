package net.mioyi.kinroll.llm

object QwenPrompt {
    fun format(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
}
