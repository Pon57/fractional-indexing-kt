@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

// Cross-terminator windows can hide shorter recursive layouts that are invisible
// to the immediate-byte capacity estimate around 0x80, so try every split while
// the rebalance is still small enough to keep candidate explosion bounded.
private const val TERMINATOR_PIVOT_SPLIT_CANDIDATE_THRESHOLD = 32
// Wide same-major byte gaps can look good under simple even spacing but still
// lose immediate headroom against the midpoint-recursive layout.
private const val MINOR_GAP_BALANCED_CANDIDATE_THRESHOLD = 32

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
    if (candidates.bestOrNull() == null || count > MINOR_GAP_BALANCED_CANDIDATE_THRESHOLD) {
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
            fractionalIndexFromOwnedRebalanceMinor(
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

    val pivot = fractionalIndexFromOwnedRebalanceMinor(
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
    val exhaustive = count <= TERMINATOR_PIVOT_SPLIT_CANDIDATE_THRESHOLD
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
