package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import kotlin.math.abs

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.candidateProjectedNextLength(
    leftMajor: Long,
    leftMinor: UByteArray,
    rightMajor: Long,
    rightMinor: UByteArray,
    candidateMajor: Long,
    candidateMinor: UByteArray,
): Int {
    val nextLeftLength = estimateMinimalBetweenLength(
        leftMajor = leftMajor,
        leftMinor = leftMinor,
        rightMajor = candidateMajor,
        rightMinor = candidateMinor,
    )
    val nextRightLength = estimateMinimalBetweenLength(
        leftMajor = candidateMajor,
        leftMinor = candidateMinor,
        rightMajor = rightMajor,
        rightMinor = rightMinor,
    )
    return maxOf(nextLeftLength, nextRightLength)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.compareCandidateScores(
    leftMajor: Long,
    leftMinor: UByteArray,
    rightMajor: Long,
    rightMinor: UByteArray,
    candidateAMajor: Long,
    candidateAMinor: UByteArray,
    candidateBMajor: Long,
    candidateBMinor: UByteArray,
): Int {
    var cmp = candidateProjectedNextLength(
        leftMajor,
        leftMinor,
        rightMajor,
        rightMinor,
        candidateAMajor,
        candidateAMinor,
    ).compareTo(
        candidateProjectedNextLength(
            leftMajor,
            leftMinor,
            rightMajor,
            rightMinor,
            candidateBMajor,
            candidateBMinor,
        ),
    )
    if (cmp != 0) return cmp
    cmp = FractionalIndex.encodedLength(candidateAMajor, candidateAMinor.size)
        .compareTo(FractionalIndex.encodedLength(candidateBMajor, candidateBMinor.size))
    if (cmp != 0) return cmp
    cmp = majorDistancePenalty(candidateAMajor).compareTo(majorDistancePenalty(candidateBMajor))
    if (cmp != 0) return cmp
    return boundaryPressure(candidateAMinor).compareTo(boundaryPressure(candidateBMinor))
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.estimateMinimalBetweenLength(
    leftMajor: Long,
    leftMinor: UByteArray,
    rightMajor: Long,
    rightMinor: UByteArray,
): Int {
    return when {
        hasNonAdjacentMajorGap(leftMajor, rightMajor) -> {
            val midMajor = midpointMajor(leftMajor, rightMajor)
            FractionalIndex.encodedLength(
                major = midMajor,
                minorSize = 1,
            )
        }

        leftMajor < rightMajor -> {
            var minLength = Int.MAX_VALUE

            if (leftMajor != 0L) {
                minLength = minOf(
                    minLength,
                    FractionalIndex.encodedLength(
                        major = leftMajor,
                        minorSize = afterMinorSize(leftMinor),
                    ),
                )
            } else {
                val leftCandidate = afterMinor(leftMinor)
                if (FractionalIndex.isEncodableMinorForMajor(0L, leftCandidate)) {
                    minLength = minOf(minLength, leftCandidate.size)
                }
            }

            if (rightMajor != 0L) {
                minLength = minOf(
                    minLength,
                    FractionalIndex.encodedLength(
                        major = rightMajor,
                        minorSize = beforeMinorSize(rightMinor),
                    ),
                )
            } else {
                val rightCandidate = beforeMinor(rightMinor)
                if (FractionalIndex.isEncodableMinorForMajor(0L, rightCandidate)) {
                    minLength = minOf(minLength, rightCandidate.size)
                }
            }

            require(minLength < Int.MAX_VALUE) { INVALID_FORMAT_MESSAGE }
            minLength
        }

        else -> {
            val minorSize = minimalBetweenMinorSize(
                left = leftMinor,
                right = rightMinor,
            )
            FractionalIndex.encodedLength(
                major = leftMajor,
                minorSize = minorSize,
            )
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.hasNonAdjacentMajorGap(
    leftMajor: Long,
    rightMajor: Long,
): Boolean {
    if (leftMajor >= rightMajor) {
        return false
    }
    // major minimum is Long.MIN_VALUE + 1, so (rightMajor - 1) is always safe.
    return leftMajor < (rightMajor - 1L)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.midpointMajor(
    leftMajor: Long,
    rightMajor: Long,
): Long {
    require(leftMajor < rightMajor) { INVALID_BOUNDS_MESSAGE }

    return if ((leftMajor < 0L) == (rightMajor < 0L)) {
        leftMajor + ((rightMajor - leftMajor) / 2L)
    } else {
        (leftMajor + rightMajor) / 2L
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.boundaryPressure(minor: UByteArray): Int {
    return abs(minor.first().toInt() - TERMINATOR_INT)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.majorDistancePenalty(major: Long): Int =
    abs(major).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
