package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal fun FractionalIndexGeneratorCore.rebalanceAroundSingleBytePivotOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    val gap = analyzeSingleBytePivotGapOrNull(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) ?: return null

    val direct = buildSingleBytePivotBalancedRebalance(
        count = count,
        major = gap.major,
        pivotIndex = gap.prefixLength,
        leftMinor = gap.leftMinor,
        rightMinor = gap.rightMinor,
        leftByte = gap.leftByte,
        rightByte = gap.rightByte,
    )
    if (count > RebalanceThresholds.SINGLE_BYTE_PIVOT_RECURSIVE_CANDIDATE) {
        return direct
    }

    val pivot = fractionalIndexFromOwnedRebalanceMinor(
        major = gap.major,
        minor = buildMinorAtPivot(
            prefix = gap.leftMinor,
            prefixLength = gap.prefixLength,
            pivotByte = (gap.leftByte + 1).toUByte(),
        ),
    )
    val remaining = count - 1
    val recursive = requireNotNull(
        value = chooseBestFixedPivotCandidateOrNull(
            lowerExclusive = lowerExclusive,
            pivot = pivot,
            upperExclusive = upperExclusive,
            requests = listOf(
                FixedPivotCandidateRequest(
                    distributedCount = remaining,
                    leftCounts = linkedSetOf(
                        remaining / 2,
                        (remaining + 1) / 2,
                        (remaining / 2) - 1,
                        0,
                        remaining,
                    ).filter { it >= 0 },
                    includePivot = true,
                ),
            ),
            depth = depth,
        ),
    ) { "single-byte pivot recursive candidate must exist within gap" }
    return bestRebalanceCandidateOrThrow(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        consider(direct)
        consider(recursive)
    }
}
