package net.mioyi.kinroll.ml.ocr

internal data class DecodedText(val text: String, val score: Float)

internal object CtcDecoder {
    fun decode(probabilities: FloatArray, steps: Int, classes: Int, dictionary: List<String>): DecodedText {
        require(steps >= 0 && classes > 1 && probabilities.size == steps * classes)
        require(dictionary.size + 1 == classes)
        val text = StringBuilder()
        var previous = 0
        var score = 0.0
        var characters = 0
        for (t in 0 until steps) {
            val offset = t * classes
            var index = 0
            for (c in 1 until classes) if (probabilities[offset + c] > probabilities[offset + index]) index = c
            if (index != 0 && index != previous) {
                text.append(dictionary[index - 1])
                score += probabilities[offset + index]
                characters++
            }
            previous = index
        }
        return DecodedText(text.toString(), if (characters == 0) 0f else (score / characters).toFloat())
    }
}
