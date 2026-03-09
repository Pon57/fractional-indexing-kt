package dev.pon.fractionalindexing

import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MIN_AVERAGE_IMPROVEMENT_RATIO = 0.01

internal data class RebalanceLengthStats(
    val avg: Double,
    val p95: Int,
    val max: Int,
)

internal fun assertStrictlySorted(keys: List<FractionalIndex>) {
    for (index in 0 until keys.lastIndex) {
        assertTrue(keys[index] < keys[index + 1], "keys are not strictly increasing at index=$index")
    }
}

internal fun generateRightEndpointBiasedSequence(
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

internal fun generateLeftEndpointBiasedSequence(
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

internal fun simulateHotspotInsertGeneratedLengths(
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

internal fun keyLengthStats(keys: List<FractionalIndex>): RebalanceLengthStats {
    val lengths = keys.map { it.bytes.size }
    return intLengthStats(lengths)
}

internal fun intLengthStats(lengths: List<Int>): RebalanceLengthStats {
    val sorted = lengths.sorted()
    val p95Index = ((sorted.size - 1) * 0.95).toInt()
    return RebalanceLengthStats(
        avg = lengths.average(),
        p95 = sorted[p95Index],
        max = sorted.last(),
    )
}

internal fun assertRebalanceParity(
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

internal fun assertAverageImprovement(
    baseline: RebalanceLengthStats,
    candidate: RebalanceLengthStats,
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
