package net.mioyi.kinroll.search

import net.mioyi.kinroll.data.PhotoDao
import net.mioyi.kinroll.llm.LlmService
import net.mioyi.kinroll.ml.clip.ClipEngine

class SearchService(
    private val photoDao: PhotoDao,
    private val clip: ClipEngine,
    private val llm: LlmService,
    private val embeddingIndex: EmbeddingIndex,
) {
    suspend fun search(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val ids = LinkedHashSet<String>()
        ids.addAll(photoDao.searchOcr("%$q%", 100))
        val english = if (q.any { it in '\u3400'..'\u9fff' }) llm.translateToEnglish(q) else q
        if (english != null) {
            val vector = clip.textEmbedding(english)
            for ((id, _) in embeddingIndex.topK(vector, 60, 0.15f)) {
                if (ids.size >= 120) break
                ids.add(id)
            }
        }
        return ids.take(120)
    }
}
