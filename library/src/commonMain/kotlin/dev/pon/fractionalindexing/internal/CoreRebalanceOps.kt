package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

// Below this count, the optimized result is compared against the balanced fallback and
// the better profile wins. Above it, the optimized result is accepted directly because
// the fallback's recursive candidate exploration becomes too expensive for diminishing
// returns — in practice the optimized strategies already dominate at larger counts.
private const val OPTIMIZED_VS_BALANCED_FALLBACK_THRESHOLD = 32

// Maximum recursion depth for the optimization pipeline. When exceeded,
// rebalanceWithinExclusiveBounds skips optimization and falls back to balanced
// binary splitting, which has O(log count) depth on its own and does not
// re-enter the pipeline.
//
// Depth consumption varies by strategy:
//  - Binary pivot splits (buildFixedPivotRebalanceCandidate): O(log count),
//    each side increments depth by 1 and halves the remaining count.
//  - Linear boundary extension (rebalanceFromExtendedLowerBoundaryOrNull):
//    O(depth) — each call consumes one key and increments depth by 1, so
//    the limit directly caps how many extensions can chain before fallback.
//
// 12 covers binary splits up to count ≈ 4096 and allows up to 12 chained
// boundary extensions before forcing the balanced fallback.
private const val MAX_REBALANCE_OPTIMIZATION_DEPTH = 12

private data class FallbackRebalanceState(
    val remaining: Int,
    val lower: FractionalIndex,
    val upper: FractionalIndex,
)

internal fun FractionalIndexGeneratorCore.rebalanceKeysOrThrow(
    count: Int,
    lowerEndpoint: FractionalIndex?,
    upperEndpoint: FractionalIndex?,
): List<FractionalIndex> {
    require(count >= 0) { NON_NEGATIVE_COUNT_MESSAGE }
    if (count == 0) {
        return emptyList()
    }

    if (lowerEndpoint == null) {
        if (upperEndpoint == null) {
            return rebalanceUnbounded(count)
        }
        return rebalanceWithUpperEndpointOnly(
            count = count,
            upperEndpoint = upperEndpoint,
        )
    }

    if (upperEndpoint == null) {
        return rebalanceWithLowerEndpointOnly(
            count = count,
            lowerEndpoint = lowerEndpoint,
        )
    }

    val endpointOrder = lowerEndpoint.compareTo(upperEndpoint)
    return when {
        endpointOrder > 0 -> throw IllegalArgumentException(INVALID_ENDPOINT_ORDER_MESSAGE)
        endpointOrder == 0 -> {
            require(count == 1) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
            listOf(lowerEndpoint)
        }

        else -> {
            require(count >= 2) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
            rebalanceWithinEndpoints(
                count = count,
                lowerEndpoint = lowerEndpoint,
                upperEndpoint = upperEndpoint,
            )
        }
    }
}

private fun FractionalIndexGeneratorCore.rebalanceUnbounded(
    count: Int,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = DEFAULT_INDEX
    generated.add(current)

    repeat(count - 1) {
        current = after(current)
        generated.add(current)
    }

    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceWithLowerEndpointOnly(
    count: Int,
    lowerEndpoint: FractionalIndex,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = lowerEndpoint
    generated.add(current)

    repeat(count - 1) {
        current = after(current)
        generated.add(current)
    }
    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceWithUpperEndpointOnly(
    count: Int,
    upperEndpoint: FractionalIndex,
): List<FractionalIndex> {
    val generated = MutableList(count) { upperEndpoint }
    var current = upperEndpoint
    for (index in count - 2 downTo 0) {
        current = before(current)
        generated[index] = current
    }
    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceWithinEndpoints(
    count: Int,
    lowerEndpoint: FractionalIndex,
    upperEndpoint: FractionalIndex,
): List<FractionalIndex> {
    if (count == 2) {
        return listOf(lowerEndpoint, upperEndpoint)
    }

    val interior = rebalanceWithinExclusiveBounds(
        count = count - 2,
        lowerExclusive = lowerEndpoint,
        upperExclusive = upperEndpoint,
    )
    return ArrayList<FractionalIndex>(count).apply {
        add(lowerEndpoint)
        addAll(interior)
        add(upperEndpoint)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.rebalanceWithinExclusiveBounds(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int = 0,
): List<FractionalIndex> {
    if (count == 0) {
        return emptyList()
    }

    if (depth < MAX_REBALANCE_OPTIMIZATION_DEPTH) {
        val optimized = rebalanceWithinExclusiveBoundsOptimizedOrNull(
            count = count,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
            depth = depth,
        )
        if (optimized != null) {
            if (count > OPTIMIZED_VS_BALANCED_FALLBACK_THRESHOLD) {
                return optimized
            }
            return bestRebalanceCandidateOrThrow(
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
            ) {
                consider(optimized)
                consider(
                    buildBalancedFallbackRebalance(
                        count = count,
                        lowerExclusive = lowerExclusive,
                        upperExclusive = upperExclusive,
                    ),
                )
            }
        }
    }

    return buildBalancedFallbackRebalance(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )
}

// Optimization pipeline: strategies are tried in order from broadest to narrowest scope.
// Each returns null when its preconditions are not met, falling through to the next.
//
// 1. Major gap        — different majors; can distribute keys across whole major slots
// 2. Single byte pivot — same major, adjacent byte gap; direct balanced split
// 3. Compact frontier  — zero-major only (count ≤ 32); greedily consumes short compact
//                        keys before minor gap, which cannot see these cross-length slots
// 4. Minor gap         — same major, same length; evenly spaced or terminator pivot split
// 5. Length boundary    — same major, different minor lengths; fills the boundary gap
private fun FractionalIndexGeneratorCore.rebalanceWithinExclusiveBoundsOptimizedOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    rebalanceAcrossMajorGapOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAroundSingleBytePivotOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAroundZeroMajorCompactFrontierOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )?.let { return it }

    rebalanceAcrossMinorGapOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAcrossLengthBoundaryOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    return null
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.buildBalancedFallbackRebalance(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val cache = HashMap<FallbackRebalanceState, List<FractionalIndex>>()

    fun buildBalancedKeys(
        remaining: Int,
        lower: FractionalIndex,
        upper: FractionalIndex,
    ): List<FractionalIndex> = cache.getOrPut(
        FallbackRebalanceState(
            remaining = remaining,
            lower = lower,
            upper = upper,
        ),
    ) {
        if (remaining == 0) {
            return@getOrPut emptyList()
        }

        val center = betweenOrThrow(lower, upper)
        if (remaining == 1) {
            return@getOrPut listOf(center)
        }

        fun buildCandidate(
            leftCount: Int,
            includeCenter: Boolean,
        ): List<FractionalIndex> {
            val rightCount = remaining - leftCount - if (includeCenter) 1 else 0
            val generated = ArrayList<FractionalIndex>(remaining)
            generated.addAll(buildBalancedKeys(leftCount, lower, center))
            if (includeCenter) {
                generated.add(center)
            }
            generated.addAll(buildBalancedKeys(rightCount, center, upper))
            return generated
        }

        val leftCount = remaining / 2
        if ((remaining % 2) != 0) {
            return@getOrPut buildCandidate(leftCount = leftCount, includeCenter = true)
        }

        bestRebalanceCandidateOrThrow(
            lowerExclusive = lower,
            upperExclusive = upper,
        ) {
            consider(buildCandidate(leftCount = leftCount, includeCenter = false))
            consider(buildCandidate(leftCount = leftCount, includeCenter = true))
            consider(buildCandidate(leftCount = leftCount - 1, includeCenter = true))
        }
    }

    return buildBalancedKeys(
        remaining = count,
        lower = lowerExclusive,
        upper = upperExclusive,
    )
}
