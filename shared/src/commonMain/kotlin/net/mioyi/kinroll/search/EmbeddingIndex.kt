package net.mioyi.kinroll.search

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mioyi.kinroll.data.EmbeddingCodec
import net.mioyi.kinroll.data.PhotoDao

class EmbeddingIndex(private val photoDao: PhotoDao) {
    private val mutex = Mutex()
    private val embeddings = mutableMapOf<String, FloatArray>()
    private var loaded = false

    private suspend fun load() {
        if (loaded) return
        for (row in photoDao.allEmbeddings()) embeddings[row.id] = EmbeddingCodec.decode(row.clipEmbedding)
        loaded = true
    }

    suspend fun put(id: String, embedding: FloatArray) = mutex.withLock {
        load()
        embeddings[id] = embedding
    }

    suspend fun remove(ids: Collection<String>) = mutex.withLock {
        if (ids.isNotEmpty()) {
            load()
            for (id in ids) embeddings.remove(id)
        }
    }

    suspend fun topK(query: FloatArray, k: Int, minScore: Float): List<Pair<String, Float>> = mutex.withLock {
        if (k <= 0) return@withLock emptyList()
        load()
        val best = ArrayList<Pair<String, Float>>(k)
        for ((id, vector) in embeddings) {
            if (vector.size != query.size) continue
            var score = 0f
            for (i in query.indices) score += query[i] * vector[i]
            if (score < minScore || !score.isFinite()) continue
            if (best.size == k && score <= best.last().second) continue
            var position = best.size
            while (position > 0 && best[position - 1].second < score) position--
            if (best.size == k) best.removeAt(k - 1)
            best.add(position, id to score)
        }
        best
    }
}
