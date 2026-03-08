package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorRebalanceTest {
    private companion object {
        const val MIN_AVERAGE_IMPROVEMENT_RATIO = 0.01
    }

    private data class LengthStats(
        val avg: Double,
        val p95: Int,
        val max: Int,
    )

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
    fun rebalance_onRightSkewedEndpointRange_reducesLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateRightEndpointBiasedSequence(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        val skewedStats = keyLengthStats(skewed)
        val rebalancedStats = keyLengthStats(rebalanced)

        assertAverageImprovement(
            baseline = skewedStats,
            candidate = rebalancedStats,
            scenario = "right-skewed endpoint rebalance",
        )
        assertTrue(
            rebalancedStats.p95 <= skewedStats.p95,
            "Expected rebalance to not worsen p95 key length. skewed=$skewedStats rebalanced=$rebalancedStats",
        )
        assertTrue(
            rebalancedStats.max <= skewedStats.max,
            "Expected rebalance to not worsen max key length. skewed=$skewedStats rebalanced=$rebalancedStats",
        )
    }

    @Test
    fun rebalance_onRightSkewedEndpointRange_reducesHotspotInsertLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateRightEndpointBiasedSequence(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        val skewedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = skewed,
                operations = 300,
            ),
        )
        val rebalancedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = rebalanced,
                operations = 300,
            ),
        )

        assertAverageImprovement(
            baseline = skewedInsertStats,
            candidate = rebalancedInsertStats,
            scenario = "right-skewed hotspot insert simulation",
        )
        assertTrue(
            rebalancedInsertStats.p95 <= skewedInsertStats.p95,
            "Expected rebalance to not worsen insert p95 key length. skewed=$skewedInsertStats rebalanced=$rebalancedInsertStats",
        )
        assertTrue(
            rebalancedInsertStats.max <= skewedInsertStats.max,
            "Expected rebalance to not worsen insert max key length. skewed=$skewedInsertStats rebalanced=$rebalancedInsertStats",
        )
    }

    @Test
    fun rebalance_onLeftSkewedEndpointRange_reducesLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateLeftEndpointBiasedSequence(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        val skewedStats = keyLengthStats(skewed)
        val rebalancedStats = keyLengthStats(rebalanced)

        assertAverageImprovement(
            baseline = skewedStats,
            candidate = rebalancedStats,
            scenario = "left-skewed endpoint rebalance",
        )
        assertTrue(
            rebalancedStats.p95 <= skewedStats.p95,
            "Expected rebalance to not worsen p95 key length. skewed=$skewedStats rebalanced=$rebalancedStats",
        )
        assertTrue(
            rebalancedStats.max <= skewedStats.max,
            "Expected rebalance to not worsen max key length. skewed=$skewedStats rebalanced=$rebalancedStats",
        )
    }

    @Test
    fun rebalance_onLeftSkewedEndpointRange_reducesHotspotInsertLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateLeftEndpointBiasedSequence(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        val skewedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = skewed,
                operations = 300,
            ),
        )
        val rebalancedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = rebalanced,
                operations = 300,
            ),
        )

        assertAverageImprovement(
            baseline = skewedInsertStats,
            candidate = rebalancedInsertStats,
            scenario = "left-skewed hotspot insert simulation",
        )
        assertTrue(
            rebalancedInsertStats.p95 <= skewedInsertStats.p95,
            "Expected rebalance to not worsen insert p95 key length. skewed=$skewedInsertStats rebalanced=$rebalancedInsertStats",
        )
        assertTrue(
            rebalancedInsertStats.max <= skewedInsertStats.max,
            "Expected rebalance to not worsen insert max key length. skewed=$skewedInsertStats rebalanced=$rebalancedInsertStats",
        )
    }

    @Test
    fun rebalance_withReversedEndpoints_returnsFailure() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        val result = FractionalIndexGenerator.rebalance(
            count = 12,
            lowerEndpoint = upper,
            upperEndpoint = lower,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "lowerEndpoint must be before upperEndpoint",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun rebalanceOrThrow_withReversedEndpoints_throws() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = 12,
                lowerEndpoint = upper,
                upperEndpoint = lower,
            )
        }
    }

    @Test
    fun rebalance_andRebalanceOrThrow_withValidInputs_returnSameSequence() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        assertRebalanceParity(
            count = 16,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        assertRebalanceParity(
            count = 16,
            lowerEndpoint = lower,
            upperEndpoint = null,
        )
        assertRebalanceParity(
            count = 16,
            lowerEndpoint = null,
            upperEndpoint = upper,
        )
        assertRebalanceParity(
            count = 16,
            lowerEndpoint = null,
            upperEndpoint = null,
        )
        assertRebalanceParity(
            count = 1,
            lowerEndpoint = lower,
            upperEndpoint = lower,
        )
        assertRebalanceParity(
            count = 0,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
    }

    @Test
    fun rebalance_withLargeCount_keepsOrderAndEndpoints() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 10_000

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

    @Test
    fun rebalance_withSameInputs_isDeterministic() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 180

        val firstRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )
        val secondRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertEquals(firstRebalanced, secondRebalanced)
    }

    @Test
    fun rebalance_withIdenticalEndpointsAndCountOne_returnsSingleEndpoint() {
        val index = FractionalIndex.default()

        val result = FractionalIndexGenerator.rebalance(
            count = 1,
            lowerEndpoint = index,
            upperEndpoint = index,
        ).getOrThrow()

        assertEquals(listOf(index), result)
    }

    @Test
    fun rebalance_withIdenticalEndpointsAndCountGreaterThanOne_returnsFailure() {
        val index = FractionalIndex.default()

        val result = FractionalIndexGenerator.rebalance(
            count = 3,
            lowerEndpoint = index,
            upperEndpoint = index,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "lowerEndpoint and upperEndpoint must define a valid range for count",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun rebalanceOrThrow_withIdenticalEndpointsAndCountGreaterThanOne_throws() {
        val index = FractionalIndex.default()

        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = 3,
                lowerEndpoint = index,
                upperEndpoint = index,
            )
        }
    }

    @Test
    fun rebalance_withDistinctEndpointsAndCountOne_returnsFailure() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        val result = FractionalIndexGenerator.rebalance(
            count = 1,
            lowerEndpoint = lower,
            upperEndpoint = upper,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "lowerEndpoint and upperEndpoint must define a valid range for count",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun rebalanceOrThrow_withDistinctEndpointsAndCountOne_throws() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = 1,
                lowerEndpoint = lower,
                upperEndpoint = upper,
            )
        }
    }

    @Test
    fun rebalance_withLowerOnly_generatesSortedKeysStartingAtLower() {
        val lower = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerEndpoint = lower,
            upperEndpoint = null,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertEquals(lower, generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withUpperOnly_generatesSortedKeysEndingAtUpper() {
        val upper = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerEndpoint = null,
            upperEndpoint = upper,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertEquals(upper, generated.last())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withoutEndpoints_startsFromDefaultAndAscending() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 5,
            lowerEndpoint = null,
            upperEndpoint = null,
        ).getOrThrow()

        assertEquals(5, generated.size)
        assertEquals(FractionalIndex.default(), generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withZeroCount_returnsEmptyList() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerEndpoint = FractionalIndex.default(),
            upperEndpoint = null,
        ).getOrThrow()

        assertTrue(generated.isEmpty())
    }

    @Test
    fun rebalance_withZeroCountAndIdenticalEndpoints_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerEndpoint = index,
            upperEndpoint = index,
        ).getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun rebalanceOrThrow_withZeroCountAndIdenticalEndpoints_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalanceOrThrow(
            count = 0,
            lowerEndpoint = index,
            upperEndpoint = index,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun rebalance_withNegativeCount_returnsFailure() {
        val result = FractionalIndexGenerator.rebalance(
            count = -1,
            lowerEndpoint = null,
            upperEndpoint = null,
        )

        assertTrue(result.isFailure)
        assertEquals("count must be non-negative", result.exceptionOrNull()?.message)
    }

    @Test
    fun rebalanceOrThrow_withNegativeCount_throws() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = -1,
                lowerEndpoint = null,
                upperEndpoint = null,
            )
        }
    }

    private fun assertStrictlySorted(keys: List<FractionalIndex>) {
        for (i in 0 until keys.lastIndex) {
            assertTrue(keys[i] < keys[i + 1], "keys are not strictly increasing at index=$i")
        }
    }

    private fun generateRightEndpointBiasedSequence(
        count: Int,
        lowerEndpoint: FractionalIndex,
        upperEndpoint: FractionalIndex,
    ): List<FractionalIndex> {
        if (count == 1) {
            return listOf(lowerEndpoint)
        }

        val generated = ArrayList<FractionalIndex>(count)
        generated.add(lowerEndpoint)
        var current = lowerEndpoint
        repeat(count - 2) {
            current = FractionalIndexGenerator.between(current, upperEndpoint).getOrThrow()
            generated.add(current)
        }
        generated.add(upperEndpoint)
        return generated
    }

    private fun generateLeftEndpointBiasedSequence(
        count: Int,
        lowerEndpoint: FractionalIndex,
        upperEndpoint: FractionalIndex,
    ): List<FractionalIndex> {
        if (count == 1) {
            return listOf(lowerEndpoint)
        }

        val interior = ArrayList<FractionalIndex>(count - 2)
        var current = upperEndpoint
        repeat(count - 2) {
            current = FractionalIndexGenerator.between(lowerEndpoint, current).getOrThrow()
            interior.add(current)
        }

        return buildList(count) {
            add(lowerEndpoint)
            addAll(interior.asReversed())
            add(upperEndpoint)
        }
    }

    private fun simulateHotspotInsertGeneratedLengths(
        initial: List<FractionalIndex>,
        operations: Int,
    ): List<Int> {
        val ordered = initial.toMutableList()
        val generatedLengths = ArrayList<Int>(operations)

        repeat(operations) { step ->
            val center = ordered.size / 2
            val offset = when (step % 3) {
                0 -> 0
                1 -> -1
                else -> 1
            }
            val insertAt = (center + offset).coerceIn(0, ordered.size)
            val left = if (insertAt == 0) {
                FractionalIndexGenerator.before(ordered.first())
            } else {
                ordered[insertAt - 1]
            }
            val right = if (insertAt == ordered.size) {
                FractionalIndexGenerator.after(ordered.last())
            } else {
                ordered[insertAt]
            }
            val generated = FractionalIndexGenerator.between(left, right).getOrThrow()
            ordered.add(insertAt, generated)
            generatedLengths.add(generated.bytes.size)
        }

        return generatedLengths
    }

    private fun keyLengthStats(keys: List<FractionalIndex>): LengthStats {
        val lengths = keys.map { it.bytes.size }
        return intLengthStats(lengths)
    }

    private fun assertRebalanceParity(
        count: Int,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ) {
        val safe = FractionalIndexGenerator.rebalance(
            count = count,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
        ).getOrThrow()
        val throwing = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
        )
        assertEquals(safe, throwing)
    }

    private fun assertAverageImprovement(
        baseline: LengthStats,
        candidate: LengthStats,
        scenario: String,
    ) {
        assertTrue(
            candidate.avg <= baseline.avg,
            "Expected $scenario to not worsen average key length. baseline=$baseline candidate=$candidate",
        )
        val improvementRatio = (baseline.avg - candidate.avg) / baseline.avg
        assertTrue(
            improvementRatio >= MIN_AVERAGE_IMPROVEMENT_RATIO,
            "Expected $scenario to improve average key length by at least ${(MIN_AVERAGE_IMPROVEMENT_RATIO * 100).toInt()}%. baseline=$baseline candidate=$candidate improvementRatio=$improvementRatio",
        )
    }

    private fun intLengthStats(lengths: List<Int>): LengthStats {
        val sorted = lengths.sorted()
        val p95Index = ((sorted.size - 1) * 0.95).toInt()
        return LengthStats(
            avg = lengths.average(),
            p95 = sorted[p95Index],
            max = sorted.last(),
        )
    }
}
