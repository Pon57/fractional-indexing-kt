@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing

import kotlin.test.Test
import kotlin.test.assertTrue

class FractionalIndexGeneratorRebalanceProfileRegressionTest {
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
}
