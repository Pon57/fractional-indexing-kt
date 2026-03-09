package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal fun FractionalIndexGeneratorCore.rebalanceAcrossMinorGapOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    val gap = analyzeSameMajorGapOrNull(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) ?: return null

    val evenlySpaced = if ((gap.rightByte - gap.leftByte - 1) >= count) {
        buildEvenlySpacedMinorGapRebalance(
            count = count,
            major = gap.major,
            prefix = gap.leftMinor,
            prefixLength = gap.prefixLength,
            leftByte = gap.leftByte,
            rightByte = gap.rightByte,
        )
    } else {
        null
    }
    val candidates = newRebalanceCandidateAccumulator(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ).apply {
        consider(evenlySpaced)
        consider(
            rebalanceAroundTerminatorMinorPivotOrNull(
                count = count,
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
                gap = gap,
                depth = depth,
            ),
        )
    }
    if (candidates.bestOrNull() == null || count > RebalanceThresholds.MINOR_GAP_BALANCED_CANDIDATE) {
        return candidates.bestOrNull()
    }
    return candidates.consider(
        buildBalancedFallbackRebalance(
            count = count,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
        ),
    ).bestOrThrow()
}

internal fun FractionalIndexGeneratorCore.buildEvenlySpacedMinorGapRebalance(
    count: Int,
    major: Long,
    prefix: UByteArray,
    prefixLength: Int,
    leftByte: Int,
    rightByte: Int,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    val divisor = count + 1
    val byteGap = rightByte - leftByte

    repeat(count) { offset ->
        val pivotByte = (leftByte + ((byteGap * (offset + 1)) / divisor)).toUByte()
        generated.add(
            fractionalIndexFromOwnedMinor(
                major = major,
                minor = buildMinorAtPivot(
                    prefix = prefix,
                    prefixLength = prefixLength,
                    pivotByte = pivotByte,
                ),
            ),
        )
    }

    return generated
}

private fun FractionalIndexGeneratorCore.rebalanceAroundTerminatorMinorPivotOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    gap: RebalanceSameMajorGapWindow,
    depth: Int,
): List<FractionalIndex>? {
    if (TERMINATOR_INT !in (gap.leftByte + 1)..<gap.rightByte) {
        return null
    }

    val pivot = fractionalIndexFromOwnedMinor(
        major = gap.major,
        minor = buildMinorAtPivot(
            prefix = gap.leftMinor,
            prefixLength = gap.prefixLength,
            pivotByte = FractionalIndex.TERMINATOR,
        ),
    )
    val leftAvailable = (TERMINATOR_INT - gap.leftByte - 1).toULong()
    val rightAvailable = (gap.rightByte - TERMINATOR_INT - 1).toULong()
    val effectiveRightAvailable = rightAvailable + extraCompactSuccessorCapacityOrZero(
        pivot = pivot,
        upperExclusive = upperExclusive,
    )
    val exhaustive = count <= RebalanceThresholds.TERMINATOR_PIVOT_SPLIT_CANDIDATE
    val bestFixedPivot = chooseBestFixedPivotCandidateOrNull(
        lowerExclusive = lowerExclusive,
        pivot = pivot,
        upperExclusive = upperExclusive,
        requests = buildCapacityBasedFixedPivotCandidateRequests(
            count = count,
            leftAvailable = leftAvailable,
            rightAvailable = effectiveRightAvailable,
            exhaustive = exhaustive,
        ),
        depth = depth,
    )
    if (!exhaustive) {
        return bestFixedPivot
    }
    return bestRebalanceCandidateOrThrow(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        consider(bestFixedPivot)
        consider(
            buildBalancedFallbackRebalance(
                count = count,
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
            ),
        )
    }
}
