package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals

class FractionalIndexGeneratorRebalanceFallbackPlanTest {
    private val lower = FractionalIndex.fromHexStringOrThrow("80")
    private val upper = FractionalIndex.fromHexStringOrThrow("8080")

    @Test
    fun rebalance_tightFallback_preservesLegacyOutputSnapshots() {
        val expectedByCount = mapOf(
            3 to listOf("80", "807f80", "8080"),
            4 to listOf("80", "807e80", "807f80", "8080"),
            6 to listOf("80", "807d80", "807e80", "807f80", "807fbf80", "8080"),
            8 to listOf(
                "80",
                "807d80",
                "807e80",
                "807ebf80",
                "807f80",
                "807f9f80",
                "807fdf80",
                "8080",
            ),
            10 to listOf(
                "80",
                "807c80",
                "807d80",
                "807e80",
                "807ebf80",
                "807f80",
                "807f9f80",
                "807fbf80",
                "807fdf80",
                "8080",
            ),
            12 to listOf(
                "80",
                "807c80",
                "807d80",
                "807e80",
                "807e9f80",
                "807f4080",
                "807f80",
                "807f8f80",
                "807faf80",
                "807fcf80",
                "807fef80",
                "8080",
            ),
        )

        expectedByCount.forEach { (count, expected) ->
            val generated = FractionalIndexGenerator.rebalanceOrThrow(
                count = count,
                lowerEndpoint = lower,
                upperEndpoint = upper,
            )
            assertEquals(
                expected,
                generated.map(FractionalIndex::toHexString),
                "tight fallback output changed for count=$count",
            )
        }
    }

    @Test
    fun rebalance_largeTightFallback_preservesCountBoundsAndOrder() {
        val count = 1_024

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
}
