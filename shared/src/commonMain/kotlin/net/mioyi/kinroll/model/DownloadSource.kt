package net.mioyi.kinroll.model

import androidx.compose.ui.text.intl.Locale

enum class DownloadSource {
    HUGGING_FACE, HF_MIRROR, MODELSCOPE;

    fun url(repo: String, path: String): String = when (this) {
        HUGGING_FACE -> "https://huggingface.co/$repo/resolve/main/$path"
        HF_MIRROR -> "https://hf-mirror.com/$repo/resolve/main/$path"
        MODELSCOPE -> "https://modelscope.cn/models/$repo/resolve/master/$path"
    }

    companion object {
        fun systemDefault(): DownloadSource =
            if (Locale.current.language.startsWith("zh")) MODELSCOPE else HUGGING_FACE
    }
}
