package net.mioyi.kinroll.ml.ocr

/** The restricted YAML scalar syntax emitted by Paddle's inference configuration. */
internal object RecDictionary {
    fun parse(ymlText: String): List<String> {
        val lines = ymlText.lineSequence().iterator()
        while (lines.hasNext()) {
            if (lines.next().trimEnd() != "  character_dict:") continue
            val result = mutableListOf<String>()
            while (lines.hasNext()) {
                val line = lines.next()
                if (!line.startsWith("  - ")) break
                result += scalar(line.substring(4))
            }
            require(result.isNotEmpty()) { "Empty OCR character dictionary" }
            return result
        }
        error("OCR character_dict not found")
    }

    private fun scalar(value: String): String {
        if (value.length >= 2 && value.first() == '\'' && value.last() == '\'') {
            return value.substring(1, value.lastIndex).replace("''", "'")
        }
        if (value.length < 2 || value.first() != '"' || value.last() != '"') return value
        return buildString {
            var i = 1
            while (i < value.lastIndex) {
                val c = value[i++]
                if (c != '\\') { append(c); continue }
                require(i < value.lastIndex) { "Incomplete YAML escape" }
                when (val escaped = value[i++]) {
                    '\\', '"', '/' -> append(escaped)
                    '0' -> append('\u0000')
                    'a' -> append('\u0007')
                    'b' -> append('\b')
                    't', '\t' -> append('\t')
                    'n' -> append('\n')
                    'v' -> append('\u000b')
                    'f' -> append('\u000c')
                    'r' -> append('\r')
                    'e' -> append('\u001b')
                    ' ' -> append(' ')
                    'N' -> append('\u0085')
                    '_' -> append('\u00a0')
                    'L' -> append('\u2028')
                    'P' -> append('\u2029')
                    'x', 'u', 'U' -> {
                        val digits = when (escaped) { 'x' -> 2; 'u' -> 4; else -> 8 }
                        require(i + digits <= value.lastIndex) { "Incomplete YAML Unicode escape" }
                        val point = value.substring(i, i + digits).toLong(16)
                        require(point in 0..0x10ffff) { "Invalid Unicode code point" }
                        if (point <= 0xffff) append(point.toInt().toChar()) else {
                            val supplementary = point.toInt() - 0x10000
                            append((0xd800 + (supplementary shr 10)).toChar())
                            append((0xdc00 + (supplementary and 1023)).toChar())
                        }
                        i += digits
                    }
                    else -> error("Unsupported YAML escape: $escaped")
                }
            }
        }
    }

    fun forClassCount(dictionary: List<String>, classCount: Int): List<String> {
        val result = if (dictionary.size + 1 < classCount) dictionary + " " else dictionary
        require(result.size + 1 == classCount) { "OCR dictionary size ${result.size} does not match $classCount classes" }
        return result
    }
}
