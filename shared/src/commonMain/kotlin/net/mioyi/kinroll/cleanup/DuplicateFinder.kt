package net.mioyi.kinroll.cleanup

import net.mioyi.kinroll.data.DupRow
import net.mioyi.kinroll.data.EmbeddingCodec
import net.mioyi.kinroll.ml.image.Quality
import kotlin.math.sqrt

object DuplicateFinder {
    fun find(rows: List<DupRow>): List<Pair<List<String>, String>> {
        if (rows.size < 2) return emptyList()
        val parents = IntArray(rows.size) { it }
        val ranks = IntArray(rows.size)
        fun root(index: Int): Int {
            var current = index
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }
        fun union(a: Int, b: Int) {
            val ra = root(a)
            val rb = root(b)
            if (ra == rb) return
            when {
                ranks[ra] < ranks[rb] -> parents[ra] = rb
                ranks[ra] > ranks[rb] -> parents[rb] = ra
                else -> { parents[rb] = ra; ranks[ra]++ }
            }
        }
        val vectors = rows.map { EmbeddingCodec.decode(it.clipEmbedding) }
        val norms = DoubleArray(rows.size) { index ->
            sqrt(vectors[index].sumOf { it.toDouble() * it })
        }
        for (i in rows.indices) {
            for (j in i + 1 until rows.size) {
                if (Quality.hamming(rows[i].dHash, rows[j].dHash) > 6) continue
                val a = vectors[i]
                val b = vectors[j]
                if (a.size != b.size || norms[i] == 0.0 || norms[j] == 0.0) continue
                var dot = 0.0
                for (k in a.indices) dot += a[k].toDouble() * b[k]
                if (dot / (norms[i] * norms[j]) >= 0.92) union(i, j)
            }
        }
        val groups = rows.indices.groupBy(::root)
        val keepOrder = compareByDescending<Int> { rows[it].blurScore ?: Float.NEGATIVE_INFINITY }
            .thenByDescending { rows[it].width.toLong() * rows[it].height }
            .thenByDescending { rows[it].takenAt ?: Long.MIN_VALUE }
            .thenBy { rows[it].id }
        return groups.values.filter { it.size >= 2 }.map { group ->
            val ids = group.sortedWith(keepOrder).map { rows[it].id }
            ids to ids.first()
        }
    }
}
