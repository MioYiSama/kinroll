package net.mioyi.kinroll.llm

/** Holds only an incomplete trailing code point between native token pieces. */
class Utf8Accumulator {
    private var buffer = ByteArray(32)
    private var size = 0

    fun push(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (size + bytes.size > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
        }
        bytes.copyInto(buffer, size)
        size += bytes.size
        var start = size - 1
        while (start >= 0 && (buffer[start].toInt() and 0xc0) == 0x80) start--
        val required = if (start < 0) 1 else when (buffer[start].toInt() and 0xff) {
            in 0xc2..0xdf -> 2
            in 0xe0..0xef -> 3
            in 0xf0..0xf4 -> 4
            else -> 1
        }
        val end = if (start >= 0 && size - start < required) start else size
        val text = buffer.decodeToString(0, end)
        buffer.copyInto(buffer, 0, end, size)
        size -= end
        return text
    }

    /** A truncated final code point is replaced, rather than silently lost. */
    fun flush(): String {
        val text = buffer.decodeToString(0, size)
        size = 0
        return text
    }
}
