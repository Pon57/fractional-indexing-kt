package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

private data class RebalanceSplitWindow(
    val totalCount: Int,
    val minLeft: Int,
    val maxLeft: Int,
    val leftAvailable: ULong,
    val rightAvailable: ULong,
)

internal data class FixedPivotCandidateRequest(
    val distributedCount: Int,
    val leftCounts: Iterable<Int>?,
    val includePivot: Boolean,
)

internal fun buildCapacityBasedFixedPivotCandidateRequests(
    count: Int,
    leftAvailable: ULong,
    rightAvailable: ULong,
    exhaustive: Boolean = false,
): List<FixedPivotCandidateRequest> = buildList(2) {
    add(
        FixedPivotCandidateRequest(
            distributedCount = count,
            leftCounts = candidateLeftCountsWithoutPivotOrNull(
                count = count,
                leftAvailable = leftAvailable,
                rightAvailable = rightAvailable,
                exhaustive = exhaustive,
            ),
            includePivot = false,
        ),
    )
    if (count > 0) {
        add(
            FixedPivotCandidateRequest(
                distributedCount = count - 1,
                leftCounts = candidateLeftCountsAroundPivotOrNull(
                    remaining = count - 1,
                    leftAvailable = leftAvailable,
                    rightAvailable = rightAvailable,
                    exhaustive = exhaustive,
                ),
                includePivot = true,
            ),
        )
    }
}

internal fun candidateLeftCountsWithoutPivotOrNull(
    count: Int,
    leftAvailable: ULong,
    rightAvailable: ULong,
    exhaustive: Boolean = false,
): List<Int>? {
    val splitWindow = buildRebalanceSplitWindowOrNull(
        totalCount = count,
        leftAvailable = leftAvailable,
        rightAvailable = rightAvailable,
    ) ?: return null
    return if (exhaustive) {
        (splitWindow.minLeft..splitWindow.maxLeft).toList()
    } else {
        listOf(splitWindow.centeredLeftCount())
    }
}

internal fun candidateLeftCountsAroundPivotOrNull(
    remaining: Int,
    leftAvailable: ULong,
    rightAvailable: ULong,
    exhaustive: Boolean = false,
): List<Int>? {
    val splitWindow = buildRebalanceSplitWindowOrNull(
        totalCount = remaining,
        leftAvailable = leftAvailable,
        rightAvailable = rightAvailable,
    ) ?: return null
    return if (exhaustive) {
        (splitWindow.minLeft..splitWindow.maxLeft).toList()
    } else {
        buildPivotCandidateLeftCounts(
            remaining = remaining,
            splitWindow = splitWindow,
            preferredLeftCount = splitWindow.proportionalLeftCount(),
        )
    }
}

private fun buildPivotCandidateLeftCounts(
    remaining: Int,
    splitWindow: RebalanceSplitWindow,
    preferredLeftCount: Int,
): List<Int> {
    return linkedSetOf(
        preferredLeftCount,
        remaining / 2,
        (remaining + 1) / 2,
        preferredLeftCount - 1,
        preferredLeftCount + 1,
        splitWindow.minLeft,
        splitWindow.maxLeft,
        0,
        remaining,
    ).filter { it in splitWindow.minLeft..splitWindow.maxLeft }
}

internal fun FractionalIndexGeneratorCore.chooseBestFixedPivotCandidateOrNull(
    lowerExclusive: FractionalIndex,
    pivot: FractionalIndex,
    upperExclusive: FractionalIndex,
    requests: Iterable<FixedPivotCandidateRequest>,
    depth: Int,
): List<FractionalIndex>? {
    return bestRebalanceCandidateOrNull(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        for (request in requests) {
            consider(
                request.leftCounts?.let { leftCounts ->
                    buildShortestFixedPivotCandidate(
                        distributedCount = request.distributedCount,
                        leftCounts = leftCounts,
                        includePivot = request.includePivot,
                        lowerExclusive = lowerExclusive,
                        pivot = pivot,
                        upperExclusive = upperExclusive,
                        depth = depth,
                    )
                },
            )
        }
    }
}

internal fun FractionalIndexGeneratorCore.buildShortestFixedPivotCandidate(
    distributedCount: Int,
    leftCounts: Iterable<Int>,
    includePivot: Boolean,
    lowerExclusive: FractionalIndex,
    pivot: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex> {
    return bestRebalanceCandidateOrThrow(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        leftCounts.forEach { leftCount ->
            consider(
                buildFixedPivotRebalanceCandidate(
                    leftCount = leftCount,
                    includePivot = includePivot,
                    rightCount = distributedCount - leftCount,
                    lowerExclusive = lowerExclusive,
                    pivot = pivot,
                    upperExclusive = upperExclusive,
                    depth = depth,
                ),
            )
        }
    }
}

internal fun FractionalIndexGeneratorCore.buildFixedPivotRebalanceCandidate(
    leftCount: Int,
    includePivot: Boolean,
    rightCount: Int,
    lowerExclusive: FractionalIndex,
    pivot: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex> {
    require(leftCount >= 0) { "leftCount must be non-negative" }
    require(rightCount >= 0) { "rightCount must be non-negative" }

    val generated = ArrayList<FractionalIndex>(leftCount + rightCount + if (includePivot) 1 else 0)
    if (leftCount > 0) {
        generated.addAll(
            rebalanceWithinExclusiveBounds(
                count = leftCount,
                lowerExclusive = lowerExclusive,
                upperExclusive = pivot,
                depth = depth + 1,
            ),
        )
    }
    if (includePivot) {
        generated.add(pivot)
    }
    if (rightCount > 0) {
        generated.addAll(
            rebalanceWithinExclusiveBounds(
                count = rightCount,
                lowerExclusive = pivot,
                upperExclusive = upperExclusive,
                depth = depth + 1,
            ),
        )
    }
    return generated
}

internal fun FractionalIndexGeneratorCore.buildSingleBytePivotBalancedRebalance(
    count: Int,
    major: Long,
    pivotIndex: Int,
    leftMinor: UByteArray,
    rightMinor: UByteArray,
    leftByte: Int,
    rightByte: Int,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    val leftCount = count / 2
    val includePivot = (count % 2) != 0
    val rightCount = count / 2
    val buildIndex: (UByteArray) -> FractionalIndex = { minor ->
        fractionalIndexFromOwnedMinor(
            major = major,
            minor = minor,
        )
    }

    for (offset in 1..leftCount) {
        generated.add(
            buildIndex(
                buildPivotMinorWithAfterRank(
                    prefix = leftMinor,
                    prefixLength = pivotIndex,
                    pivotByte = leftByte.toUByte(),
                    rank = offset,
                ),
            ),
        )
    }

    if (includePivot) {
        generated.add(
            buildIndex(
                buildMinorAtPivot(
                    prefix = leftMinor,
                    prefixLength = pivotIndex,
                    pivotByte = (leftByte + 1).toUByte(),
                ),
            ),
        )
    }

    for (offset in rightCount downTo 1) {
        generated.add(
            buildIndex(
                buildPivotMinorWithBeforeRank(
                    prefix = rightMinor,
                    prefixLength = pivotIndex,
                    pivotByte = rightByte.toUByte(),
                    rank = offset,
                ),
            ),
        )
    }

    return generated
}

internal fun FractionalIndexGeneratorCore.extraCompactSuccessorCapacityOrZero(
    pivot: FractionalIndex,
    upperExclusive: FractionalIndex,
): ULong {
    if (pivot != DEFAULT_INDEX) {
        return 0uL
    }

    val compactSuccessor = after(pivot)
    return if (compactSuccessor < upperExclusive) 1uL else 0uL
}

private fun buildRebalanceSplitWindowOrNull(
    totalCount: Int,
    leftAvailable: ULong,
    rightAvailable: ULong,
): RebalanceSplitWindow? {
    require(totalCount >= 0) { "totalCount must be non-negative" }
    if (totalCount == 0) {
        return RebalanceSplitWindow(
            totalCount = 0,
            minLeft = 0,
            maxLeft = 0,
            leftAvailable = leftAvailable,
            rightAvailable = rightAvailable,
        )
    }

    val totalCountUnsigned = totalCount.toULong()
    if ((leftAvailable + rightAvailable) < totalCountUnsigned) {
        return null
    }

    val minLeft = if (rightAvailable >= totalCountUnsigned) {
        0
    } else {
        (totalCountUnsigned - rightAvailable).toInt()
    }
    val maxLeft = minOf(leftAvailable, totalCountUnsigned).toInt()
    return RebalanceSplitWindow(
        totalCount = totalCount,
        minLeft = minLeft,
        maxLeft = maxLeft,
        leftAvailable = leftAvailable,
        rightAvailable = rightAvailable,
    )
}

private fun RebalanceSplitWindow.centeredLeftCount(): Int =
    (totalCount / 2).coerceIn(minLeft, maxLeft)

private fun RebalanceSplitWindow.proportionalLeftCount(): Int {
    if (totalCount == 0) {
        return 0
    }

    val tc = totalCount.toULong()
    val totalAvailable = leftAvailable + rightAvailable
    val proportional = if (leftAvailable <= ULong.MAX_VALUE / tc) {
        ((tc * leftAvailable) / totalAvailable).toInt()
    } else {
        // leftAvailable is too large for direct multiplication; divide first.
        // May be off by 1 from rounding, which is acceptable for a proportional hint.
        (leftAvailable / (totalAvailable / tc)).coerceAtMost(tc).toInt()
    }
    return proportional.coerceIn(minLeft, maxLeft)
}
