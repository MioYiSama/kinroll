package net.mioyi.kinroll.cleanup

import net.mioyi.kinroll.data.DupRow
import net.mioyi.kinroll.data.EmbeddingCodec
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateFinderTest {
    private fun vector(cosine: Float = 1f): FloatArray = FloatArray(512).apply {
        this[0] = cosine
        this[1] = sqrt(1f - cosine * cosine)
    }
    private fun row(
        id: String, hash: Long = 0, embedding: FloatArray = vector(), blur: Float? = 100f,
        width: Int = 100, height: Int = 100, takenAt: Long? = 1,
    ) = DupRow(id, "photo://$id", hash, EmbeddingCodec.encode(embedding), blur, width, height, takenAt, 1024)

    @Test fun emptyAndSingletonHaveNoGroups() {
        assertTrue(DuplicateFinder.find(emptyList()).isEmpty())
        assertTrue(DuplicateFinder.find(listOf(row("a"))).isEmpty())
    }

    @Test fun hammingSixIncludedButSevenExcluded() {
        assertEquals(listOf("a", "b"), DuplicateFinder.find(listOf(row("a"), row("b", hash = 63))).single().first)
        assertTrue(DuplicateFinder.find(listOf(row("a"), row("b", hash = 127))).isEmpty())
    }

    @Test fun bothHashAndCosineThresholdsMustPass() {
        assertTrue(DuplicateFinder.find(listOf(row("a"), row("b", embedding = vector(0.919f)))).isEmpty())
        assertEquals(1, DuplicateFinder.find(listOf(row("a"), row("b", embedding = vector(0.921f)))).size)
        assertTrue(DuplicateFinder.find(listOf(row("a"), row("b", hash = 127, embedding = vector()))).isEmpty())
    }

    @Test fun unionFindGroupsTransitiveMatches() {
        // A-B and B-C differ by six bits; A-C differs by twelve.
        val result = DuplicateFinder.find(listOf(row("a", 0), row("b", 63), row("c", 4095)))
        assertEquals(listOf("a", "b", "c"), result.single().first)
        assertEquals("a", result.single().second)
    }

    @Test fun keepRuleUsesSharpnessThenPixelCountThenDate() {
        val rows = listOf(
            row("large", blur = 90f, width = 1000, height = 1000, takenAt = 100),
            row("small", blur = 100f, width = 100, height = 100, takenAt = 100),
            row("older", blur = 100f, width = 200, height = 100, takenAt = 1),
            row("newer", blur = 100f, width = 200, height = 100, takenAt = 2),
        )
        val result = DuplicateFinder.find(rows).single()
        assertEquals("newer", result.second)
        assertEquals(listOf("newer", "older", "small", "large"), result.first)
    }

    @Test fun missingSharpnessAndDateSortBehindKnownValues() {
        val result = DuplicateFinder.find(listOf(row("unknown", blur = null), row("old", takenAt = null), row("known"))).single()
        assertEquals(listOf("known", "old", "unknown"), result.first)
        assertEquals("known", result.second)
    }

    @Test fun disjointGroupsRemainSeparateAndSingletonExcluded() {
        val result = DuplicateFinder.find(listOf(row("a"), row("b"), row("c", -1), row("d", -1), row("e", 0xFFFF)))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result.map { it.first })
    }

    @Test fun zeroEmbeddingsCannotBeCosineMatches() {
        assertTrue(DuplicateFinder.find(listOf(row("a", embedding = FloatArray(512)), row("b"))).isEmpty())
    }

    @Test fun cosineIsIndependentOfMagnitude() {
        val scaled = vector().also { it[0] = 5f }
        assertEquals(1, DuplicateFinder.find(listOf(row("a"), row("b", embedding = scaled))).size)
    }

    @Test fun pixelCountUsesLongWithoutOverflow() {
        val result = DuplicateFinder.find(listOf(row("small"), row("huge", width = 50000, height = 50000))).single()
        assertEquals("huge", result.second)
    }
}
