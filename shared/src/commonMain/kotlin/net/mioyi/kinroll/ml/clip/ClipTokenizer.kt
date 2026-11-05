package net.mioyi.kinroll.ml.clip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

class ClipTokenizer private constructor(
    private val vocabulary: Map<String, Int>,
    private val ranks: Map<Pair<String, String>, Int>,
) {
    internal val endToken: Long = vocabulary.getValue("<|endoftext|>").toLong()
    private val startToken = vocabulary.getValue("<|startoftext|>").toLong()

    fun encode(text: String): LongArray {
        val result = LongArray(77)
        result[0] = startToken
        var cursor = 1
        val normalized = ClipTextPieces.normalize(text)
        for (piece in ClipTextPieces.split(normalized)) {
            val bytes = piece.encodeToByteArray()
            var symbols = bytes.map { byteUnicode[it.toInt() and 255].toString() }.toMutableList()
            if (symbols.isEmpty()) continue
            symbols[symbols.lastIndex] += "</w>"
            while (symbols.size > 1) {
                var bestRank = Int.MAX_VALUE
                var bestPair: Pair<String, String>? = null
                for (i in 0 until symbols.lastIndex) {
                    val pair = symbols[i] to symbols[i + 1]
                    val rank = ranks[pair] ?: continue
                    if (rank < bestRank) {
                        bestRank = rank
                        bestPair = pair
                    }
                }
                val pair = bestPair ?: break
                val merged = ArrayList<String>(symbols.size)
                var i = 0
                while (i < symbols.size) {
                    if (i < symbols.lastIndex && symbols[i] == pair.first && symbols[i + 1] == pair.second) {
                        merged.add(symbols[i] + symbols[i + 1])
                        i += 2
                    } else merged.add(symbols[i++])
                }
                symbols = merged
            }
            for (symbol in symbols) {
                if (cursor == 76) break
                result[cursor++] = (vocabulary[symbol] ?: endToken.toInt()).toLong()
            }
            if (cursor == 76) break
        }
        result[cursor] = endToken
        return result
    }

    companion object {
        private val byteUnicode: CharArray = run {
            val visible = (33..126).toList() + (161..172).toList() + (174..255).toList()
            val result = CharArray(256)
            for (byte in visible) result[byte] = byte.toChar()
            var extra = 256
            for (byte in 0..255) if (result[byte] == '\u0000') result[byte] = (extra++).toChar()
            result
        }

        fun fromJson(text: String): ClipTokenizer {
            val model = Json.parseToJsonElement(text).jsonObject.getValue("model").jsonObject
            val vocabulary = model.getValue("vocab").jsonObject.mapValues { it.value.jsonPrimitive.int }
            require("<|startoftext|>" in vocabulary && "<|endoftext|>" in vocabulary)
            val ranks = LinkedHashMap<Pair<String, String>, Int>()
            for ((rank, merge) in model.getValue("merges").jsonArray.withIndex()) {
                val pair = if (merge is JsonArray) {
                    require(merge.size == 2)
                    merge[0].jsonPrimitive.content to merge[1].jsonPrimitive.content
                } else {
                    val value = merge.jsonPrimitive.content
                    val split = value.indexOf(' ')
                    require(split > 0 && split < value.lastIndex)
                    value.substring(0, split) to value.substring(split + 1)
                }
                if (pair !in ranks) ranks[pair] = rank
            }
            return ClipTokenizer(vocabulary, ranks)
        }
    }
}
