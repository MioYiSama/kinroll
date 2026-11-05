package net.mioyi.kinroll.model

enum class ModelGroup { CLIP, OCR, LLM }

data class ModelFile(
    val id: String,
    val repo: String,
    val path: String,
    val sizeBytes: Long,
    val group: ModelGroup,
    val exactSize: Boolean,
)

object ModelManifest {
    val files: List<ModelFile> = listOf(
        ModelFile("clip_vision", "Xenova/mobileclip_s2", "onnx/vision_model.onnx", 143020962L, ModelGroup.CLIP, true),
        ModelFile("clip_text", "Xenova/mobileclip_s2", "onnx/text_model.onnx", 253894023L, ModelGroup.CLIP, true),
        ModelFile("clip_tokenizer", "Xenova/mobileclip_s2", "tokenizer.json", 2224081L, ModelGroup.CLIP, false),
        ModelFile("ocr_det", "PaddlePaddle/PP-OCRv6_small_det_onnx", "inference.onnx", 9880512L, ModelGroup.OCR, true),
        ModelFile("ocr_rec", "PaddlePaddle/PP-OCRv6_small_rec_onnx", "inference.onnx", 21159378L, ModelGroup.OCR, true),
        ModelFile("ocr_rec_config", "PaddlePaddle/PP-OCRv6_small_rec_onnx", "inference.yml", 150579L, ModelGroup.OCR, false),
        ModelFile("llm", "unsloth/Qwen3.5-0.8B-GGUF", "Qwen3.5-0.8B-UD-Q4_K_XL.gguf", 558772480L, ModelGroup.LLM, true),
    )

    fun file(id: String): ModelFile = files.first { it.id == id }
}
