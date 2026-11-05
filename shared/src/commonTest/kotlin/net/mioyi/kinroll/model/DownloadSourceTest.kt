package net.mioyi.kinroll.model

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadSourceTest {
    @Test fun huggingFaceUrlPreservesRepositoryAndNestedPath() {
        assertEquals("https://huggingface.co/Xenova/mobileclip_s2/resolve/main/onnx/vision_model.onnx",
            DownloadSource.HUGGING_FACE.url("Xenova/mobileclip_s2", "onnx/vision_model.onnx"))
    }

    @Test fun mirrorUsesMainBranch() {
        assertEquals("https://hf-mirror.com/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx",
            DownloadSource.HF_MIRROR.url("PaddlePaddle/PP-OCRv6_small_det_onnx", "inference.onnx"))
    }

    @Test fun modelScopeUsesModelsPrefixAndMasterBranch() {
        assertEquals("https://modelscope.cn/models/unsloth/Qwen3.5-0.8B-GGUF/resolve/master/Qwen3.5-0.8B-UD-Q4_K_XL.gguf",
            DownloadSource.MODELSCOPE.url("unsloth/Qwen3.5-0.8B-GGUF", "Qwen3.5-0.8B-UD-Q4_K_XL.gguf"))
    }

    @Test fun defaultFollowsSystemLanguage() {
        val expected = if (Locale.current.language.startsWith("zh")) DownloadSource.MODELSCOPE else DownloadSource.HUGGING_FACE
        assertEquals(expected, DownloadSource.systemDefault())
    }

    @Test fun manifestHasSevenUniqueModelsAndCorrectValidationRules() {
        assertEquals(7, ModelManifest.files.size)
        assertEquals(7, ModelManifest.files.map { it.id }.toSet().size)
        assertEquals(listOf(143020962L, 253894023L, 2224081L, 9880512L, 21159378L, 150579L, 558772480L), ModelManifest.files.map { it.sizeBytes })
        for (file in ModelManifest.files) {
            assertEquals(file, ModelManifest.file(file.id))
            assertEquals(file.path.endsWith(".onnx") || file.path.endsWith(".gguf"), file.exactSize)
            assertTrue(file.sizeBytes > 0)
            assertFalse(file.path.startsWith('/'))
        }
        assertEquals(3, ModelManifest.files.count { it.group == ModelGroup.CLIP })
        assertEquals(3, ModelManifest.files.count { it.group == ModelGroup.OCR })
        assertEquals(1, ModelManifest.files.count { it.group == ModelGroup.LLM })
    }
}
