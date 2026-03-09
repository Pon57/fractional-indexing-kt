package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceZeroMajorOptimizationTest {
    @Test
    fun rebalance_crossingDefaultPivot_consumesCompactSuccessorBeforeLongerKeys() {
        val lower = FractionalIndex.fromHexString("7b80").getOrThrow()
        val upper = FractionalIndex.fromHexString("8480").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 11,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(
            listOf("7b80", "7c80", "7d80", "7e80", "7f80", "80", "8080", "8180", "8280", "8380", "8480"),
            generated.map { it.toHexString() },
        )
        assertEquals(21, generated.sumOf { it.bytes.size })
    }

    @Test
    fun rebalance_crossingDefaultPivot_withInsufficientPivotCapacity_fallsBackWithoutThrowing() {
        val lower = FractionalIndex.fromHexString("7b80").getOrThrow()
        val upper = FractionalIndex.fromHexString("8180").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 9,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_crossingDefaultPivot_inTightRange_prefersShorterMixedLengthProfile() {
        val lower = FractionalIndex.fromHexString("7f80").getOrThrow()
        val upper = FractionalIndex.fromHexString("82bf80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
        assertTrue(
            generated.map { it.toHexString() }.containsAll(listOf("80", "8080", "8180", "8280")),
            "Expected rebalance to consume compact 1/2-byte keys before touching longer-bound gaps.",
        )
        assertTrue(
            generated.windowed(size = 2)
                .none { (left, right) -> left.bytes.size == 3 && right.bytes.size == 3 },
            "Expected rebalance to avoid clustering longer keys while short-bound gaps still exist.",
        )
        assertEquals(15, generated.sumOf { it.bytes.size })
        assertEquals(1, generated.subList(1, generated.lastIndex).count { it.bytes.size == 3 })
    }

    @Test
    fun rebalance_crossingDefaultPivot_inTighterRange_spreadsThreeByteKeysAcrossCompactGaps() {
        val lower = FractionalIndex.fromHexString("7f80").getOrThrow()
        val upper = FractionalIndex.fromHexString("82bf80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 9,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
        assertTrue(
            generated.map { it.toHexString() }.containsAll(listOf("80", "8080", "8180", "8280")),
            "Expected rebalance to keep the compact frontier intact before exhausting longer gaps.",
        )
        assertTrue(
            generated.windowed(size = 2).none { (left, right) -> left.bytes.size == 3 && right.bytes.size == 3 },
            "Expected 3-byte keys to stay separated by shorter keys in the tight zero-major window.",
        )
    }

    @Test
    fun rebalance_crossingDefaultPivot_keepsImmediateFollowUpInsertionsWithinThreeBytes() {
        val lower = FractionalIndex.fromHexString("7c80").getOrThrow()
        val upper = FractionalIndex.fromHexString("8180").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 10,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(
            3,
            nextInsertionLengths.max(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    @Test
    fun rebalance_withDefaultLowerEndpoint_usesCompactTierBeforeExtraThreeByteKeys() {
        val lower = FractionalIndex.default()
        val upper = FractionalIndex.fromHexString("8480").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
        assertTrue(
            generated.map { it.toHexString() }.containsAll(listOf("8080", "8180", "8280", "8380")),
            "Expected rebalance to keep all available compact 2-byte keys before emitting extra 3-byte keys.",
        )
        assertEquals(1, generated.subList(1, generated.lastIndex).count { it.bytes.size == 3 })
        assertEquals(
            3,
            nextInsertionLengths.max(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    @Test
    fun rebalance_fallback_evenCount_keeps_shorter_midpoint_profile() {
        val lower = FractionalIndex.fromHexString("80").getOrThrow()
        val upper = FractionalIndex.fromHexString("800280").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 4,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(
            listOf("80", "800080", "800180", "800280"),
            generated.map { it.toHexString() },
        )
    }

    @Test
    fun rebalance_acrossMajorGap_crossingZero_prefersDefaultPivotInProfile() {
        val lower = FractionalIndex.fromMajorMinor(-10L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val upper = FractionalIndex.fromMajorMinor(10L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertStrictlySorted(generated)
        assertTrue(
            generated.any { it == FractionalIndex.default() },
            "Expected rebalance to include the zero-major pivot when it shortens the profile.",
        )
    }
}
