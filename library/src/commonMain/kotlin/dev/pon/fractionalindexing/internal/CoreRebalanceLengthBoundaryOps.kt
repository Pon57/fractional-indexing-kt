package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal fun FractionalIndexGeneratorCore.rebalanceAcrossLengthBoundaryOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    val boundary = analyzeLengthBoundaryWindowOrNull(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) ?: return null

    return if (boundary.shorterLower) {
        bestRebalanceCandidateOrNull(
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
        ) {
            consider(
                if ((boundary.boundaryByte - TERMINATOR_INT - 1) >= count) {
                    buildEvenlySpacedMinorGapRebalance(
                        count = count,
                        major = boundary.major,
                        prefix = boundary.boundaryMinor,
                        prefixLength = boundary.prefixLength,
                        leftByte = TERMINATOR_INT,
                        rightByte = boundary.boundaryByte,
                    )
                } else {
                    null
                },
            )
            consider(
                rebalanceFromExtendedLowerBoundaryOrNull(
                    count = count,
                    lowerExclusive = lowerExclusive,
                    upperExclusive = upperExclusive,
                    depth = depth,
                ),
            )
        }
    } else {
        if ((TERMINATOR_INT - boundary.boundaryByte - 1) < count) {
            return null
        }
        buildEvenlySpacedMinorGapRebalance(
            count = count,
            major = boundary.major,
            prefix = boundary.boundaryMinor,
            prefixLength = boundary.prefixLength,
            leftByte = boundary.boundaryByte,
            rightByte = TERMINATOR_INT,
        )
    }
}

private fun FractionalIndexGeneratorCore.rebalanceFromExtendedLowerBoundaryOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    if (count == 0) {
        return emptyList()
    }

    val pivot = fractionalIndexFromOwnedRebalanceMinor(
        major = lowerExclusive.major,
        minor = extendMinorWithTerminator(lowerExclusive.minor),
    )
    if (pivot <= lowerExclusive || pivot >= upperExclusive) {
        return null
    }

    // This candidate is intentionally one-sided: it exists to harvest the first
    // shorter key unlocked by extending the lower boundary. We only keep it when
    // that asymmetric profile beats the more balanced alternatives above.
    val generated = ArrayList<FractionalIndex>(count)
    generated.add(pivot)
    if (count > 1) {
        generated.addAll(
            rebalanceWithinExclusiveBounds(
                count = count - 1,
                lowerExclusive = pivot,
                upperExclusive = upperExclusive,
                depth = depth + 1,
            ),
        )
    }
    return generated
}
