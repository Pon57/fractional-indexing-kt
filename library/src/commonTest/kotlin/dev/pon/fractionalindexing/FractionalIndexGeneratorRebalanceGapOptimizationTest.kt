@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceGapOptimizationTest {
    @Test
    fun rebalance_singleBytePivotGap_uses_midpoint_layout_when_it_improves_follow_up_gaps() {
        val lower = FractionalIndex.fromHexString("7fa080").getOrThrow()
        val upper = FractionalIndex.fromHexString("7fa280").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 4,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(4, generated.subList(1, generated.lastIndex).maxOf { it.bytes.size })
        assertEquals(7, generated.subList(1, generated.lastIndex).sumOf { it.bytes.size })
        assertEquals(
            4,
            nextInsertionLengths.max(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
        assertEquals(
            12,
            nextInsertionLengths.sum(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    @Test
    fun rebalance_acrossMajorGap_prefers_layout_with_shorter_follow_up_insertions() {
        val lower = FractionalIndex.fromHexString("2080").getOrThrow()
        val upper = FractionalIndex.fromHexString("c180").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 17,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(
            2,
            nextInsertionLengths.maxOrNull(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
        assertEquals(
            32,
            nextInsertionLengths.sum(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    @Test
    fun rebalance_acrossMinorGap_crossingTerminator_prefersShorterLengthProfile() {
        val lower = FractionalIndex.fromHexString("4080").getOrThrow()
        val upper = FractionalIndex.fromHexString("bf80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertStrictlySorted(generated)
        assertTrue(
            generated.any { it == FractionalIndex.default() },
            "Expected rebalance to reuse the compact terminator pivot when it shortens the profile.",
        )
        assertEquals(9, generated.sumOf { it.bytes.size })
    }

    @Test
    fun rebalance_acrossWideMinorGap_prefers_midpoint_layout_when_follow_up_headroom_is_better() {
        val lower = FractionalIndex.fromHexString("397080").getOrThrow()
        val upper = FractionalIndex.fromHexString("439f80").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(
            listOf("3b80", "3d80", "3f80"),
            interior.map { it.toHexString() },
        )
        assertEquals(2, nextInsertionLengths.max(), "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths")
        assertEquals(8, nextInsertionLengths.sum(), "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths")
    }

    @Test
    fun rebalance_crossTerminatorWindow_keeps_follow_up_insertions_within_three_bytes() {
        val lower = FractionalIndex.fromHexString("789080").getOrThrow()
        val upper = FractionalIndex.fromHexString("829080").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 20,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)
        val nextInsertionLengths = generated
            .windowed(size = 2)
            .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

        assertEquals(3, interior.maxOf { it.bytes.size }, "generated=${generated.map { it.toHexString() }}")
        assertEquals(43, interior.sumOf { it.bytes.size }, "generated=${generated.map { it.toHexString() }}")
        assertEquals(
            3,
            nextInsertionLengths.max(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
        assertEquals(
            57,
            nextInsertionLengths.sum(),
            "generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
        )
    }

    @Test
    fun rebalance_crossTerminatorWindow_family_avoids_four_byte_follow_up_gaps() {
        val lower = FractionalIndex.fromHexString("789080").getOrThrow()
        val upperEndpoints = listOf("829080", "829480", "829880", "829c80", "82a080")

        upperEndpoints.forEach { upperHex ->
            val upper = FractionalIndex.fromHexString(upperHex).getOrThrow()
            val generated = FractionalIndexGenerator.rebalanceOrThrow(
                count = 20,
                lowerEndpoint = lower,
                upperEndpoint = upper,
            )
            val nextInsertionLengths = generated
                .windowed(size = 2)
                .map { (left, right) -> FractionalIndexGenerator.between(left, right).getOrThrow().bytes.size }

            assertEquals(
                3,
                nextInsertionLengths.max(),
                "upper=$upperHex generated=${generated.map { it.toHexString() }} next=$nextInsertionLengths",
            )
        }
    }
}
