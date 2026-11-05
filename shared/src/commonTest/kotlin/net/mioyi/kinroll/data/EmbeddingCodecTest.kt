package net.mioyi.kinroll.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmbeddingCodecTest {
    @Test
    fun encodeWritesFloat32InLittleEndianOrder() {
        assertContentEquals(
            byteArrayOf(
                0x00, 0x00, 0x80.toByte(), 0x3f,
                0x00, 0x00, 0x20, 0xc0.toByte(),
                0x78, 0x56, 0x34, 0x12,
            ),
            EmbeddingCodec.encode(floatArrayOf(1f, -2.5f, Float.fromBits(0x12345678))),
        )
    }

    @Test
    fun decodeReadsLittleEndianBytesWithoutSignExtension() {
        val decoded = EmbeddingCodec.decode(
            byteArrayOf(
                0x00, 0x00, 0x80.toByte(), 0x3f,
                0x00, 0x00, 0x20, 0xc0.toByte(),
                0xff.toByte(), 0xff.toByte(), 0x7f, 0x7f,
            ),
        )
        assertContentEquals(floatArrayOf(1f, -2.5f, Float.MAX_VALUE), decoded)
    }

    @Test
    fun roundTripPreservesIeee754RawBits() {
        val bits = intArrayOf(
            0,
            Int.MIN_VALUE,
            1,
            Int.MIN_VALUE + 1,
            0x007fffff,
            0x00800000,
            0x7f7fffff,
            0x7f800000,
            0xff800000.toInt(),
            0x7fc12345,
            0xffc12345.toInt(),
        )
        val original = FloatArray(bits.size) { Float.fromBits(bits[it]) }
        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(original))
        assertContentEquals(bits, IntArray(decoded.size) { decoded[it].toRawBits() })
    }

    @Test
    fun clipSizedEmbeddingUses2048BytesAndRoundTrips() {
        val embedding = FloatArray(512) { (it - 256) / 512f }
        val encoded = EmbeddingCodec.encode(embedding)
        assertEquals(2048, encoded.size)
        assertContentEquals(embedding, EmbeddingCodec.decode(encoded))
    }

    @Test
    fun emptyEmbeddingRoundTrips() {
        assertContentEquals(byteArrayOf(), EmbeddingCodec.encode(floatArrayOf()))
        assertContentEquals(floatArrayOf(), EmbeddingCodec.decode(byteArrayOf()))
    }

    @Test
    fun decodeRejectsPartialFloatRatherThanDroppingBytes() {
        for (size in listOf(1, 2, 3, 5, 6, 7)) {
            assertFailsWith<IllegalArgumentException> { EmbeddingCodec.decode(ByteArray(size)) }
        }
    }
}
