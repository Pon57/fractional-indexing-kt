package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex.Companion.TERMINATOR

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.shouldFallbackToMinimal(left: UByteArray, right: UByteArray): Boolean {
    if (left.size == 1 && left[0] == TERMINATOR) {
        return right.isNotEmpty() && right[0].toInt() == TERMINATOR_INT + 1
    }

    if (right.size == 1 && right[0] == TERMINATOR) {
        return left.isNotEmpty() && left[0].toInt() == TERMINATOR_INT - 1
    }

    return false
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.newBetweenSpreadBalanced(
    major: Long,
    left: UByteArray,
    right: UByteArray,
): UByteArray {
    val comparisonLength = minOf(left.size, right.size)

    for (i in 0 until comparisonLength) {
        val leftByte = left[i].toInt()
        val rightByte = right[i].toInt()

        midpointBetweenOrNull(left, i, leftByte, rightByte)?.let { return it }

        if (leftByte == rightByte - 1) {
            val leftCandidate = if (i + 1 < left.size) {
                spliceAfterSpread(
                    source = left,
                    prefixEndExclusive = i + 1,
                    tailStart = i + 1,
                )
            } else {
                null
            }
            val rightCandidate = if (i + 1 < right.size) {
                spliceBeforeSpread(
                    source = right,
                    prefixEndExclusive = i + 1,
                    tailStart = i + 1,
                )
            } else {
                null
            }
            chooseSpreadCandidate(
                major = major,
                left = left,
                right = right,
                pivot = i,
                leftCandidate = leftCandidate,
                rightCandidate = rightCandidate,
            )?.let { return it }
        }

        if (leftByte > rightByte) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
    }

    return resolveLengthBoundary(
        left = left,
        right = right,
        split = comparisonLength,
        spliceBefore = ::spliceBeforeSpread,
        spliceAfter = ::spliceAfterSpread,
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.chooseSpreadCandidate(
    major: Long,
    left: UByteArray,
    right: UByteArray,
    pivot: Int,
    leftCandidate: UByteArray?,
    rightCandidate: UByteArray?,
): UByteArray? {
    if (leftCandidate == null) {
        return rightCandidate
    }
    if (rightCandidate == null) {
        return leftCandidate
    }

    val sizeDiff = leftCandidate.size - rightCandidate.size
    if (sizeDiff != 0) {
        return if (sizeDiff < 0) leftCandidate else rightCandidate
    }

    val leftPressure = candidatePressure(
        candidate = leftCandidate,
        pivot = pivot,
    )
    val rightPressure = candidatePressure(
        candidate = rightCandidate,
        pivot = pivot,
    )
    if (leftPressure != rightPressure) {
        return if (leftPressure < rightPressure) leftCandidate else rightCandidate
    }

    val leftTailLength = left.size - (pivot + 1)
    val rightTailLength = right.size - (pivot + 1)
    if (leftTailLength != rightTailLength) {
        return if (leftTailLength < rightTailLength) leftCandidate else rightCandidate
    }

    val cmp = compareCandidateScores(
        leftMajor = major,
        leftMinor = left,
        rightMajor = major,
        rightMinor = right,
        candidateAMajor = major,
        candidateAMinor = leftCandidate,
        candidateBMajor = major,
        candidateBMinor = rightCandidate,
    )
    return if (cmp <= 0) leftCandidate else rightCandidate
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.candidatePressure(
    candidate: UByteArray,
    pivot: Int,
): Int {
    val maxNonTerminatorIndex = (candidate.lastIndex - 1).coerceAtLeast(0)
    val index = (pivot + 1).coerceIn(0, maxNonTerminatorIndex)
    return kotlin.math.abs(candidate[index].toInt() - TERMINATOR_INT)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.minimalBetweenMinor(
    left: UByteArray,
    right: UByteArray,
): UByteArray {
    val shorterLength = minOf(left.size, right.size) - 1

    for (i in 0 until shorterLength) {
        val leftByte = left[i].toInt()
        val rightByte = right[i].toInt()

        midpointBetweenOrNull(left, i, leftByte, rightByte)?.let { return it }

        if (leftByte == rightByte - 1) {
            return spliceAfterMinimal(
                source = left,
                prefixEndExclusive = i + 1,
                tailStart = i + 1,
            )
        }

        if (leftByte > rightByte) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
    }

    return resolveLengthBoundary(
        left = left,
        right = right,
        split = shorterLength + 1,
        spliceBefore = ::spliceBeforeMinimal,
        spliceAfter = ::spliceAfterMinimal,
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.minimalBetweenMinorSize(
    left: UByteArray,
    right: UByteArray,
): Int {
    val shorterLength = minOf(left.size, right.size) - 1

    for (i in 0 until shorterLength) {
        val leftByte = left[i].toInt()
        val rightByte = right[i].toInt()

        if (leftByte < rightByte - 1) {
            return i + 2
        }

        if (leftByte == rightByte - 1) {
            return spliceAfterMinimalSize(left, i + 1)
        }

        if (leftByte > rightByte) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
    }

    return resolveLengthBoundarySizeOnly(left, right, shorterLength + 1)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.resolveLengthBoundary(
    left: UByteArray,
    right: UByteArray,
    split: Int,
    spliceBefore: (UByteArray, Int, Int) -> UByteArray,
    spliceAfter: (UByteArray, Int, Int) -> UByteArray,
): UByteArray {
    return when {
        left.size < right.size -> {
            if (right[split - 1] < TERMINATOR) {
                throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
            }
            spliceBefore(right, split, split)
        }

        left.size > right.size -> {
            if (left[split - 1] >= TERMINATOR) {
                throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
            }
            spliceAfter(left, split, split)
        }

        else -> throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.resolveLengthBoundarySizeOnly(
    left: UByteArray,
    right: UByteArray,
    split: Int,
): Int {
    return when {
        left.size < right.size -> {
            if (right[split - 1] < TERMINATOR) {
                throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
            }
            spliceBeforeMinimalSize(right, split)
        }

        left.size > right.size -> {
            if (left[split - 1] >= TERMINATOR) {
                throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
            }
            spliceAfterMinimalSize(left, split)
        }

        else -> throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.midpointBetweenOrNull(
    left: UByteArray,
    index: Int,
    leftByte: Int,
    rightByte: Int,
): UByteArray? {
    if (leftByte >= rightByte - 1) {
        return null
    }

    val bytes = left.terminatedCopyOfRange(0, index + 1)
    bytes[index] = (leftByte + (rightByte - leftByte) / 2).toUByte()
    return bytes
}
