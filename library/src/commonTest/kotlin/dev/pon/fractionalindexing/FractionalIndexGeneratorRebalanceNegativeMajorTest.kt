@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceNegativeMajorTest {

    // --- Major gap within negative territory ---

    @Test
    fun rebalance_negativeMajorGap_keepsOrderAndEndpoints() {
        // major=-20 (0x2c80) to major=-5 (0x3b80): 14 majors available
        val lower = FractionalIndex.fromHexString("2c80").getOrThrow()
        val upper = FractionalIndex.fromHexString("3b80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 8,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(8, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_negativeMajorGap_producesEvenlySpacedShortKeys() {
        // major=-30 (0x2280) to major=-10 (0x3680): 19 majors available, request 5
        val lower = FractionalIndex.fromHexString("2280").getOrThrow()
        val upper = FractionalIndex.fromHexString("3680").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertTrue(
            interior.all { it.bytes.size == 2 },
            "Expected all interior keys to be short (2-byte) when major gap is wide. generated=${generated.map { it.toHexString() }}",
        )
    }

    @Test
    fun rebalance_negativeMajorGap_followUpInsertionsStayShort() {
        // major=-20 (0x2c80) to major=-5 (0x3b80): 14 majors, request 7
        val lower = FractionalIndex.fromHexString("2c80").getOrThrow()
        val upper = FractionalIndex.fromHexString("3b80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(
            2,
            nextInsertionLengths.max(),
            "Expected follow-up insertions to stay at 2 bytes within a wide negative major gap. " +
                "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    // --- Same negative major, minor gap ---

    @Test
    fun rebalance_sameNegativeMajor_minorGap_keepsOrderAndEndpoints() {
        // Both major=-5 (tag=0x3B), minor bytes differ: 0x60 vs 0xA0
        val lower = FractionalIndex.fromHexString("3b6080").getOrThrow()
        val upper = FractionalIndex.fromHexString("3ba080").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 6,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(6, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_sameNegativeMajor_crossTerminator_prefersCompactPivot() {
        // major=-5 (tag=0x3B), minor: 0x60..0xA0 crosses terminator (0x80)
        val lower = FractionalIndex.fromHexString("3b6080").getOrThrow()
        val upper = FractionalIndex.fromHexString("3ba080").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)

        assertTrue(
            interior.any { it.toHexString() == "3b80" },
            "Expected rebalance to place the terminator-pivot key (major=-5, minor=[0x80]) in the interior. " +
                "generated=${generated.map { it.toHexString() }}",
        )
    }

    // --- Single byte pivot gap in negative major ---

    @Test
    fun rebalance_sameNegativeMajor_singleBytePivotGap_producesValidLayout() {
        // major=-5, adjacent minor bytes: 0x90 vs 0x92 (gap of 2, single byte pivot)
        val lower = FractionalIndex.fromHexString("3b9080").getOrThrow()
        val upper = FractionalIndex.fromHexString("3b9280").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 4,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(4, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    // --- Length boundary in negative major ---

    @Test
    fun rebalance_sameNegativeMajor_lengthBoundary_keepsOrderAndEndpoints() {
        // major=-5 (tag=0x3B), shorter minor [0x80] vs longer minor [0x85, 0x80]
        val lower = FractionalIndex.fromHexString("3b80").getOrThrow()
        val upper = FractionalIndex.fromHexString("3b8580").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(5, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_sameNegativeMajor_lengthBoundary_restoresShortKeys() {
        // major=-5 (tag=0x3B), [0x80] to [0x85, 0x80] → 4 available bytes (0x81..0x84)
        val lower = FractionalIndex.fromHexString("3b80").getOrThrow()
        val upper = FractionalIndex.fromHexString("3b8580").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 6,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)

        assertTrue(
            interior.all { it.bytes.size == 3 },
            "Expected all interior keys to be 3-byte in a tight length boundary window. " +
                "generated=${generated.map { it.toHexString() }}",
        )
    }

    // --- Medium negative major (beyond short tier) ---

    @Test
    fun rebalance_mediumNegativeMajorGap_keepsOrderAndEndpoints() {
        // major=-100: tag=0x16, second=0xFF-(100-42)=0xC5 → "16c580"
        // major=-50:  tag=0x16, second=0xFF-(50-42)=0xF7  → "16f780"
        val lower = FractionalIndex.fromMajorMinor(-100L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val upper = FractionalIndex.fromMajorMinor(-50L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 10,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(10, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_mediumNegativeMajorGap_followUpInsertionsStayShort() {
        // major=-100 to major=-50: 49 majors available, request 6
        val lower = FractionalIndex.fromMajorMinor(-100L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val upper = FractionalIndex.fromMajorMinor(-50L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 6,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(
            3,
            nextInsertionLengths.max(),
            "Expected follow-up insertions to stay at 3 bytes in medium negative major gap. " +
                "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    // --- Symmetry: negative mirrors positive ---

    @Test
    fun rebalance_negativeMajorGap_profileIsSymmetricWithPositive() {
        // Negative: major=-10 to major=-3 (gap=6)
        // Positive: major=3 to major=10 (gap=6)
        val negLower = FractionalIndex.fromMajorMinor(-10L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val negUpper = FractionalIndex.fromMajorMinor(-3L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val posLower = FractionalIndex.fromMajorMinor(3L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val posUpper = FractionalIndex.fromMajorMinor(10L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val negGenerated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = negLower,
            upperEndpoint = negUpper,
        )
        val posGenerated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = posLower,
            upperEndpoint = posUpper,
        )

        val negStats = keyLengthStats(negGenerated)
        val posStats = keyLengthStats(posGenerated)

        assertEquals(
            posStats.max,
            negStats.max,
            "Expected symmetric max key length between negative and positive major ranges.",
        )
        assertEquals(
            posStats.avg,
            negStats.avg,
            "Expected symmetric average key length between negative and positive major ranges.",
        )
    }

    // --- Large count in negative territory ---

    @Test
    fun rebalance_negativeMajorGap_largeCount_keepsOrderAndStability() {
        val lower = FractionalIndex.fromMajorMinor(-200L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val upper = FractionalIndex.fromMajorMinor(-1L, ubyteArrayOf(FractionalIndex.TERMINATOR))
        val count = 100

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(count, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    // --- One-sided negative endpoints ---

    @Test
    fun rebalance_withNegativeLowerOnly_generatesSortedKeysStartingAtLower() {
        val lower = FractionalIndex.fromMajorMinor(-10L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 8,
            lowerEndpoint = lower,
            upperEndpoint = null,
        )

        assertEquals(8, generated.size)
        assertEquals(lower, generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withNegativeUpperOnly_generatesSortedKeysEndingAtUpper() {
        val upper = FractionalIndex.fromMajorMinor(-5L, ubyteArrayOf(FractionalIndex.TERMINATOR))

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 8,
            lowerEndpoint = null,
            upperEndpoint = upper,
        )

        assertEquals(8, generated.size)
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }
}
