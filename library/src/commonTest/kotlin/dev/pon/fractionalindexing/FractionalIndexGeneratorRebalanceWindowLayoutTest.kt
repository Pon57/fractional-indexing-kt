package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceWindowLayoutTest {
    @Test
    fun rebalance_withBothEndpoints_includesEndpointsAndKeepsOrder() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        val generated = FractionalIndexGenerator.rebalance(
            count = 30,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        ).getOrThrow()

        assertEquals(30, generated.size)
        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_tightEndpointWindow_consumesShortestTiersBeforeFallingBackToLongerKeys() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after().after()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertTrue(
            interior.any { it == FractionalIndex.default() },
            "Expected rebalance to keep using the compact default pivot in a tight endpoint window.",
        )
        assertTrue(
            interior.any { it.toHexString() == "8080" },
            "Expected rebalance to consume the remaining 2-byte compact slot before emitting more 3-byte keys.",
        )
        assertEquals(12, interior.sumOf { it.bytes.size })
    }

    @Test
    fun rebalance_tightEndpointWindow_evenCount_usesShortestAvailableLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after().after()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 4,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(
            listOf("7f80", "80", "8080", "8180"),
            generated.map { it.toHexString() },
        )
    }

    @Test
    fun rebalance_acrossMinorGap_collapsesTerminatorPivotToShortestKey() {
        val lower = FractionalIndex.fromHexString("7f80").getOrThrow()
        val upper = FractionalIndex.fromHexString("8380").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 5,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(FractionalIndex.default(), generated[1])
        assertEquals(1, generated[1].bytes.size)
    }

    @Test
    fun rebalance_acrossLengthBoundaryGap_restoresCompactSequentialKeys() {
        val lower = FractionalIndex.default()
        val upper = FractionalIndex.fromHexString("8580").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 6,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val interior = generated.subList(1, generated.lastIndex)

        assertEquals(lower, generated.first())
        assertEquals(upper, generated.last())
        assertTrue(
            interior.all { it.bytes.size == 2 },
            "generated=${generated.map { it.toHexString() }}",
        )
        assertEquals(
            8,
            interior.sumOf { it.bytes.size },
            "generated=${generated.map { it.toHexString() }}",
        )
    }

    @Test
    fun rebalance_acrossLengthBoundaryGap_exhaustingDirectGap_keepsCompactSequentialKeys() {
        val lower = FractionalIndex.default()
        val upper = FractionalIndex.fromHexString("8580").getOrThrow()

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = 7,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(
            listOf("80", "8080", "8180", "8280", "8380", "8480", "8580"),
            generated.map { it.toHexString() },
        )
    }

    @Test
    fun rebalance_withTerminatorBoundaryIntervals_acceptsValidLengthBoundaryRanges() {
        val scenarios = listOf(
            "80" to "8080",
            "80" to "807f80",
            "7f80" to "7f8080",
            "8080" to "808080",
        )

        scenarios.forEach { (lowerHex, upperHex) ->
            val lower = FractionalIndex.fromHexString(lowerHex).getOrThrow()
            val upper = FractionalIndex.fromHexString(upperHex).getOrThrow()

            val generated = FractionalIndexGenerator.rebalanceOrThrow(
                count = 3,
                lowerEndpoint = lower,
                upperEndpoint = upper,
            )

            assertEquals(lower, generated.first(), "lower=$lowerHex upper=$upperHex")
            assertEquals(upper, generated.last(), "lower=$lowerHex upper=$upperHex")
            assertStrictlySorted(generated)
        }
    }
}
