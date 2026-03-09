package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal fun FractionalIndexGeneratorCore.rebalanceAcrossMajorGapOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    if (!hasNonAdjacentMajorGap(lowerExclusive.major, upperExclusive.major)) {
        return null
    }

    val availableMajors = (upperExclusive.major.toULong() - lowerExclusive.major.toULong()) - 1uL
    if (availableMajors < count.toULong()) {
        return null
    }
    if (count == 1) {
        return listOf(betweenOrThrow(lowerExclusive, upperExclusive))
    }

    return bestRebalanceCandidateOrThrow(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        consider(
            buildEvenlySpacedMajorRebalance(
                count = count,
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
            ),
        )
        consider(
            rebalanceAroundZeroMajorPivotOrNull(
                count = count,
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
                depth = depth,
            ),
        )
        if (count <= RebalanceThresholds.MAJOR_GAP_EDGE_CANDIDATE) {
            consider(
                rebalanceFromLowerMajorEdgeOrNull(
                    count = count,
                    lowerExclusive = lowerExclusive,
                    upperExclusive = upperExclusive,
                ),
            )
            consider(
                rebalanceFromUpperMajorEdgeOrNull(
                    count = count,
                    lowerExclusive = lowerExclusive,
                    upperExclusive = upperExclusive,
                ),
            )
        }
    }
}

private fun FractionalIndexGeneratorCore.rebalanceFromLowerMajorEdgeOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex>? {
    if (count == 0) {
        return emptyList()
    }

    val first = after(lowerExclusive)
    if (first >= upperExclusive) {
        return null
    }

    val generated = ArrayList<FractionalIndex>(count)
    generated.add(first)
    if (count > 1) {
        // Use balanced fallback instead of rebalanceWithinExclusiveBounds to avoid
        // recursive optimization explosion when the sub-range is still a major gap.
        generated.addAll(
            buildBalancedFallbackRebalance(
                count = count - 1,
                lowerExclusive = first,
                upperExclusive = upperExclusive,
            ),
        )
    }
    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceFromUpperMajorEdgeOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex>? {
    if (count == 0) {
        return emptyList()
    }

    val last = before(upperExclusive)
    if (last <= lowerExclusive) {
        return null
    }

    val generated = ArrayList<FractionalIndex>(count)
    if (count > 1) {
        // Use balanced fallback instead of rebalanceWithinExclusiveBounds to avoid
        // recursive optimization explosion when the sub-range is still a major gap.
        generated.addAll(
            buildBalancedFallbackRebalance(
                count = count - 1,
                lowerExclusive = lowerExclusive,
                upperExclusive = last,
            ),
        )
    }
    generated.add(last)
    return generated
}

private fun FractionalIndexGeneratorCore.buildEvenlySpacedMajorRebalance(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    val divisor = (count + 1).toULong()
    val gap = upperExclusive.major.toULong() - lowerExclusive.major.toULong()
    val quotient = gap / divisor
    val remainder = gap % divisor
    val lowerUnsignedMajor = lowerExclusive.major.toULong()

    repeat(count) { offset ->
        val step = (offset + 1).toULong()
        val delta = (quotient * step) + ((remainder * step) / divisor)
        val major = (lowerUnsignedMajor + delta).toLong()
        generated.add(
            FractionalIndex.fromMajorMinor(
                major = major,
                minor = DEFAULT_MINOR,
            ),
        )
    }

    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceAroundZeroMajorPivotOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    if (lowerExclusive.major >= 0L || upperExclusive.major <= 0L) {
        return null
    }

    val pivot = DEFAULT_INDEX
    val leftAvailable = (-lowerExclusive.major).toULong() - 1uL
    val rightAvailable = upperExclusive.major.toULong() - 1uL
    return chooseBestFixedPivotCandidateOrNull(
        lowerExclusive = lowerExclusive,
        pivot = pivot,
        upperExclusive = upperExclusive,
        requests = buildCapacityBasedFixedPivotCandidateRequests(
            count = count,
            leftAvailable = leftAvailable,
            rightAvailable = rightAvailable,
        ),
        depth = depth,
    )
}
