package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal class RebalanceSameMajorGapWindow(
    val major: Long,
    val leftMinor: UByteArray,
    val rightMinor: UByteArray,
    val prefixLength: Int,
    val leftByte: Int,
    val rightByte: Int,
)

internal class RebalanceLengthBoundaryWindow(
    val major: Long,
    val boundaryMinor: UByteArray,
    val prefixLength: Int,
    val shorterLower: Boolean,
    val boundaryByte: Int,
)

internal fun FractionalIndexGeneratorCore.analyzeSameMajorGapOrNull(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): RebalanceSameMajorGapWindow? {
    if (lowerExclusive.major != upperExclusive.major) {
        return null
    }

    val leftMinor = lowerExclusive.minor
    val rightMinor = upperExclusive.minor
    val comparisonLength = minOf(leftMinor.size, rightMinor.size) - 1
    for (index in 0 until comparisonLength) {
        val leftByte = leftMinor[index].toInt()
        val rightByte = rightMinor[index].toInt()
        if (leftByte == rightByte) {
            continue
        }
        if (leftByte > rightByte) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
        return RebalanceSameMajorGapWindow(
            major = lowerExclusive.major,
            leftMinor = leftMinor,
            rightMinor = rightMinor,
            prefixLength = index,
            leftByte = leftByte,
            rightByte = rightByte,
        )
    }

    return null
}

internal fun FractionalIndexGeneratorCore.analyzeSingleBytePivotGapOrNull(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): RebalanceSameMajorGapWindow? {
    val gap = analyzeSameMajorGapOrNull(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) ?: return null
    if (gap.leftMinor.size != gap.rightMinor.size || gap.leftMinor.size < 2) {
        return null
    }

    val pivotIndex = gap.leftMinor.lastIndex - 1
    if (gap.prefixLength != pivotIndex) {
        return null
    }
    if (gap.rightByte != gap.leftByte + 2) {
        return null
    }
    return gap
}

internal fun FractionalIndexGeneratorCore.analyzeLengthBoundaryWindowOrNull(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): RebalanceLengthBoundaryWindow? {
    if (lowerExclusive.major != upperExclusive.major) {
        return null
    }

    val leftMinor = lowerExclusive.minor
    val rightMinor = upperExclusive.minor
    if (leftMinor.size == rightMinor.size) {
        return null
    }

    val sharedPrefixLength = minOf(leftMinor.size, rightMinor.size) - 1
    for (index in 0 until sharedPrefixLength) {
        if (leftMinor[index] != rightMinor[index]) {
            return null
        }
    }

    return if (leftMinor.size < rightMinor.size) {
        val boundaryByte = rightMinor[sharedPrefixLength].toInt()
        if (boundaryByte < TERMINATOR_INT) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
        RebalanceLengthBoundaryWindow(
            major = lowerExclusive.major,
            boundaryMinor = rightMinor,
            prefixLength = sharedPrefixLength,
            shorterLower = true,
            boundaryByte = boundaryByte,
        )
    } else {
        val boundaryByte = leftMinor[sharedPrefixLength].toInt()
        if (boundaryByte >= TERMINATOR_INT) {
            throw IllegalArgumentException(INVALID_BOUNDS_MESSAGE)
        }
        RebalanceLengthBoundaryWindow(
            major = lowerExclusive.major,
            boundaryMinor = leftMinor,
            prefixLength = sharedPrefixLength,
            shorterLower = false,
            boundaryByte = boundaryByte,
        )
    }
}
