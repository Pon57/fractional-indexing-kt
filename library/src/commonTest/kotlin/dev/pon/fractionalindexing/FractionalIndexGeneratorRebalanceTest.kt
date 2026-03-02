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
    fun rebalance_withBothBounds_generatesSortedKeysInsideBounds() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        val generated = FractionalIndexGenerator.rebalance(
            count = 30,
            lowerExclusive = lower,
            upperExclusive = upper,
        ).getOrThrow()

        assertEquals(30, generated.size)
        assertStrictlySorted(generated)
        assertTrue(generated.first() > lower, "first generated key must be greater than lowerExclusive")
        assertTrue(generated.last() < upper, "last generated key must be less than upperExclusive")
    }

    @Test
    fun rebalance_onSkewedWindow_reducesLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateRightAnchoredWithinBounds(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )

        val skewedStats = keyLengthStats(skewed)
        val rebalancedStats = keyLengthStats(rebalanced)

        assertAverageImprovement(
            baseline = skewedStats,
            candidate = rebalancedStats,
            scenario = "window rebalance",
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
    fun rebalance_onSkewedWindow_reducesHotspotInsertLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateRightAnchoredWithinBounds(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )

        val skewedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = skewed,
                lowerExclusive = lower,
                upperExclusive = upper,
                operations = 300,
            ),
        )
        val rebalancedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = rebalanced,
                lowerExclusive = lower,
                upperExclusive = upper,
                operations = 300,
            ),
        )

        assertAverageImprovement(
            baseline = skewedInsertStats,
            candidate = rebalancedInsertStats,
            scenario = "hotspot insert simulation",
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
    fun rebalance_onLeftSkewedWindow_reducesLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateLeftAnchoredWithinBounds(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )

        val skewedStats = keyLengthStats(skewed)
        val rebalancedStats = keyLengthStats(rebalanced)

        assertAverageImprovement(
            baseline = skewedStats,
            candidate = rebalancedStats,
            scenario = "left-skewed window rebalance",
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
    fun rebalance_onLeftSkewedWindow_reducesHotspotInsertLengthProfile() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 120
        val skewed = generateLeftAnchoredWithinBounds(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        val rebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )

        val skewedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = skewed,
                lowerExclusive = lower,
                upperExclusive = upper,
                operations = 300,
            ),
        )
        val rebalancedInsertStats = intLengthStats(
            simulateHotspotInsertGeneratedLengths(
                initial = rebalanced,
                lowerExclusive = lower,
                upperExclusive = upper,
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
    fun rebalance_acceptsUnorderedBounds() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        val forward = FractionalIndexGenerator.rebalance(
            count = 12,
            lowerExclusive = lower,
            upperExclusive = upper,
        ).getOrThrow()
        val reversed = FractionalIndexGenerator.rebalance(
            count = 12,
            lowerExclusive = upper,
            upperExclusive = lower,
        ).getOrThrow()

        assertEquals(forward, reversed)
    }

    @Test
    fun rebalance_andRebalanceOrThrow_withValidInputs_returnSameSequence() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()

        assertRebalanceParity(
            count = 16,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        assertRebalanceParity(
            count = 16,
            lowerExclusive = lower,
            upperExclusive = null,
        )
        assertRebalanceParity(
            count = 16,
            lowerExclusive = null,
            upperExclusive = upper,
        )
        assertRebalanceParity(
            count = 16,
            lowerExclusive = null,
            upperExclusive = null,
        )
        assertRebalanceParity(
            count = 0,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
    }

    @Test
    fun rebalance_withLargeCount_keepsOrderAndBounds() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 10_000

        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = upper,
            upperExclusive = lower,
        )

        assertEquals(count, generated.size)
        assertStrictlySorted(generated)
        assertTrue(generated.first() > lower, "first generated key must be greater than lowerExclusive")
        assertTrue(generated.last() < upper, "last generated key must be less than upperExclusive")
    }

    @Test
    fun rebalance_withSameInputs_isDeterministic() {
        val lower = FractionalIndex.default().before()
        val upper = FractionalIndex.default().after()
        val count = 180

        val firstRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lower,
            upperExclusive = upper,
        )
        val secondRebalanced = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = upper,
            upperExclusive = lower,
        )

        assertEquals(firstRebalanced, secondRebalanced)
    }

    @Test
    fun rebalance_withIdenticalBounds_returnsFailure() {
        val index = FractionalIndex.default()

        val result = FractionalIndexGenerator.rebalance(
            count = 3,
            lowerExclusive = index,
            upperExclusive = index,
        )

        assertTrue(result.isFailure)
        assertEquals("bounds must be distinct", result.exceptionOrNull()?.message)
    }

    @Test
    fun rebalanceOrThrow_withIdenticalBounds_throws() {
        val index = FractionalIndex.default()

        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = 3,
                lowerExclusive = index,
                upperExclusive = index,
            )
        }
    }

    @Test
    fun rebalance_withLowerOnly_generatesSortedKeysAfterLower() {
        val lower = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerExclusive = lower,
            upperExclusive = null,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertStrictlySorted(generated)
        assertTrue(generated.first() > lower, "first generated key must be greater than lowerExclusive")
    }

    @Test
    fun rebalance_withUpperOnly_generatesSortedKeysBeforeUpper() {
        val upper = FractionalIndex.default()

        val generated = FractionalIndexGenerator.rebalance(
            count = 10,
            lowerExclusive = null,
            upperExclusive = upper,
        ).getOrThrow()

        assertEquals(10, generated.size)
        assertStrictlySorted(generated)
        assertTrue(generated.last() < upper, "last generated key must be less than upperExclusive")
    }

    @Test
    fun rebalance_withoutBounds_startsFromDefaultAndAscending() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 5,
            lowerExclusive = null,
            upperExclusive = null,
        ).getOrThrow()

        assertEquals(5, generated.size)
        assertEquals(FractionalIndex.default(), generated.first())
        assertStrictlySorted(generated)
    }

    @Test
    fun rebalance_withZeroCount_returnsEmptyList() {
        val generated = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerExclusive = FractionalIndex.default(),
            upperExclusive = null,
        ).getOrThrow()

        assertTrue(generated.isEmpty())
    }

    @Test
    fun rebalance_withZeroCountAndIdenticalBounds_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalance(
            count = 0,
            lowerExclusive = index,
            upperExclusive = index,
        ).getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun rebalanceOrThrow_withZeroCountAndIdenticalBounds_returnsEmptyList() {
        val index = FractionalIndex.default()
        val result = FractionalIndexGenerator.rebalanceOrThrow(
            count = 0,
            lowerExclusive = index,
            upperExclusive = index,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun rebalance_withNegativeCount_returnsFailure() {
        val result = FractionalIndexGenerator.rebalance(
            count = -1,
            lowerExclusive = null,
            upperExclusive = null,
        )

        assertTrue(result.isFailure)
        assertEquals("count must be non-negative", result.exceptionOrNull()?.message)
    }

    @Test
    fun rebalanceOrThrow_withNegativeCount_throws() {
        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.rebalanceOrThrow(
                count = -1,
                lowerExclusive = null,
                upperExclusive = null,
            )
        }
    }

    private fun assertStrictlySorted(keys: List<FractionalIndex>) {
        for (i in 0 until keys.lastIndex) {
            assertTrue(keys[i] < keys[i + 1], "keys are not strictly increasing at index=$i")
        }
    }

    private fun generateRightAnchoredWithinBounds(
        count: Int,
        lowerExclusive: FractionalIndex,
        upperExclusive: FractionalIndex,
    ): List<FractionalIndex> {
        val generated = ArrayList<FractionalIndex>(count)
        var current = lowerExclusive
        repeat(count) {
            current = FractionalIndexGenerator.between(current, upperExclusive).getOrThrow()
            generated.add(current)
        }
        return generated
    }

    private fun generateLeftAnchoredWithinBounds(
        count: Int,
        lowerExclusive: FractionalIndex,
        upperExclusive: FractionalIndex,
    ): List<FractionalIndex> {
        val generated = ArrayList<FractionalIndex>(count)
        var current = upperExclusive
        repeat(count) {
            current = FractionalIndexGenerator.between(lowerExclusive, current).getOrThrow()
            generated.add(current)
        }
        generated.reverse()
        return generated
    }

    private fun simulateHotspotInsertGeneratedLengths(
        initial: List<FractionalIndex>,
        lowerExclusive: FractionalIndex,
        upperExclusive: FractionalIndex,
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
            val left = if (insertAt == 0) lowerExclusive else ordered[insertAt - 1]
            val right = if (insertAt == ordered.size) upperExclusive else ordered[insertAt]
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
        lowerExclusive: FractionalIndex?,
        upperExclusive: FractionalIndex?,
    ) {
        val safe = FractionalIndexGenerator.rebalance(
            count = count,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
        ).getOrThrow()
        val throwing = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
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
