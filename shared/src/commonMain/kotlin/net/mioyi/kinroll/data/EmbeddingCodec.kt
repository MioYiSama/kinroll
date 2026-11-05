package net.mioyi.kinroll.data

object EmbeddingCodec {
    fun encode(v: FloatArray): ByteArray {
        val bytes = ByteArray(v.size * Float.SIZE_BYTES)
        for (i in v.indices) {
            val bits = v[i].toRawBits()
            val offset = i * Float.SIZE_BYTES
            bytes[offset] = bits.toByte()
            bytes[offset + 1] = (bits ushr 8).toByte()
            bytes[offset + 2] = (bits ushr 16).toByte()
            bytes[offset + 3] = (bits ushr 24).toByte()
        }
        return bytes
    }

    fun decode(b: ByteArray): FloatArray {
        require(b.size % Float.SIZE_BYTES == 0) { "Embedding bytes must contain complete float32 values" }
        val values = FloatArray(b.size / Float.SIZE_BYTES)
        for (i in values.indices) {
            val offset = i * Float.SIZE_BYTES
            val bits = (b[offset].toInt() and 0xff) or
                ((b[offset + 1].toInt() and 0xff) shl 8) or
                ((b[offset + 2].toInt() and 0xff) shl 16) or
                ((b[offset + 3].toInt() and 0xff) shl 24)
            values[i] = Float.fromBits(bits)
        }
        return values
    }
}
