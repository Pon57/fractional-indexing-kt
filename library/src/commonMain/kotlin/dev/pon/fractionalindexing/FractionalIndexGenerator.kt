package dev.pon.fractionalindexing

import dev.pon.fractionalindexing.FractionalIndex.Companion.TERMINATOR

@OptIn(ExperimentalUnsignedTypes::class)
public object FractionalIndexGenerator {
    public enum class BetweenStrategy {
        MINIMAL,
        SPREAD,
    }

    private val TERMINATOR_INT = TERMINATOR.toInt()
    private val UBYTE_MAX_INT = UByte.MAX_VALUE.toInt()
    private const val INVALID_BOUNDS_MESSAGE = "lower bound must be smaller than upper bound"
    private const val DISTINCT_BOUNDS_MESSAGE = "bounds must be distinct"

    public fun before(index: FractionalIndex): FractionalIndex {
        return FractionalIndex(newBefore(index.unsafeRawBytes))
    }

    public fun after(index: FractionalIndex): FractionalIndex {
        return FractionalIndex(newAfter(index.unsafeRawBytes))
    }

    public fun between(
        first: FractionalIndex,
        second: FractionalIndex,
        strategy: BetweenStrategy = BetweenStrategy.SPREAD,
    ): Result<FractionalIndex> {
        return runCatching {
            val comparison = first.compareTo(second)
            require(comparison != 0) { DISTINCT_BOUNDS_MESSAGE }

            val (left, right) = if (comparison < 0) {
                first.unsafeRawBytes to second.unsafeRawBytes
            } else {
                second.unsafeRawBytes to first.unsafeRawBytes
            }
            FractionalIndex(generateBetween(left, right, strategy))
        }
    }

    private fun generateBetween(
        left: UByteArray,
        right: UByteArray,
        strategy: BetweenStrategy,
    ): UByteArray {
        return when (strategy) {
            BetweenStrategy.MINIMAL -> newBetweenMinimal(left, right)
            BetweenStrategy.SPREAD -> {
                if (shouldFallbackToMinimal(left, right)) {
                    newBetweenMinimal(left, right)
                } else {
                    newBetweenSpread(left, right)
                }
            }
        }
    }

    private fun newBetweenMinimal(left: UByteArray, right: UByteArray): UByteArray {
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

    private fun newBetweenSpread(left: UByteArray, right: UByteArray): UByteArray {
        val comparisonLength = minOf(left.size, right.size)

        for (i in 0 until comparisonLength) {
            val leftByte = left[i].toInt()
            val rightByte = right[i].toInt()

            midpointBetweenOrNull(left, i, leftByte, rightByte)?.let { return it }

            if (leftByte == rightByte - 1 && i + 1 < left.size) {
                return spliceAfterSpread(
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
            split = comparisonLength,
            spliceBefore = ::spliceBeforeSpread,
            spliceAfter = ::spliceAfterSpread,
        )
    }

    private fun midpointBetweenOrNull(
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

    private fun resolveLengthBoundary(
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

    private fun shouldFallbackToMinimal(left: UByteArray, right: UByteArray): Boolean {
        // For repeated one-sided inserts near the root, MINIMAL must be used.
        // SPREAD grows keys faster in this pattern, so enforce MINIMAL for [0x80]..[0x81,*] and [0x7F,*]..[0x80].
        if (left.size == 1 && left[0] == TERMINATOR) {
            return right.isNotEmpty() && right[0].toInt() == TERMINATOR_INT + 1
        }

        if (right.size == 1 && right[0] == TERMINATOR) {
            return left.isNotEmpty() && left[0].toInt() == TERMINATOR_INT - 1
        }

        return false
    }

    private fun newBefore(bytes: UByteArray): UByteArray {
        for (i in bytes.indices) {
            val current = bytes[i].toInt()

            if (current > TERMINATOR_INT) {
                return bytes.terminatedCopyOfRange(0, i)
            }

            if (current > 0) {
                val out = bytes.terminatedCopyOfRange(0, i + 1)
                out[i] = (current - 1).toUByte()
                return out
            }
        }

        error("Invalid fractional index: missing valid decrement point")
    }

    private fun newAfter(bytes: UByteArray): UByteArray {
        for (i in bytes.indices) {
            val current = bytes[i].toInt()

            if (current < TERMINATOR_INT) {
                return bytes.terminatedCopyOfRange(0, i)
            }

            if (current < UBYTE_MAX_INT) {
                val out = bytes.terminatedCopyOfRange(0, i + 1)
                out[i] = (current + 1).toUByte()
                return out
            }
        }

        error("Invalid fractional index: missing valid increment point")
    }

    private fun spliceBeforeMinimal(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
    ): UByteArray {
        for (index in tailStart until source.size) {
            val current = source[index].toInt()

            if (current > TERMINATOR_INT) {
                return buildSplicedCopy(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = index - tailStart,
                )
            }

            if (current > 0) {
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = (current - 1).toUByte(),
                )
            }
        }

        error("Invalid fractional index: missing valid decrement point")
    }

    private fun spliceAfterMinimal(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
    ): UByteArray {
        for (index in tailStart until source.size) {
            val current = source[index].toInt()

            if (current < TERMINATOR_INT) {
                return buildSplicedCopy(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = index - tailStart,
                )
            }

            if (current < UBYTE_MAX_INT) {
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = (current + 1).toUByte(),
                )
            }
        }

        error("Invalid fractional index: missing valid increment point")
    }

    private fun spliceBeforeSpread(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
    ): UByteArray {
        for (index in tailStart until source.size) {
            val current = source[index].toInt()

            if (current > TERMINATOR_INT) {
                val target = TERMINATOR_INT + ((current - TERMINATOR_INT) / 2)
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = minOf(current - 1, target).toUByte(),
                )
            }

            if (current > 0) {
                val target = current / 2
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = minOf(current - 1, target).toUByte(),
                )
            }
        }

        error("Invalid fractional index: missing valid decrement point")
    }

    private fun spliceAfterSpread(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
    ): UByteArray {
        for (index in tailStart until source.size) {
            val current = source[index].toInt()

            if (current < TERMINATOR_INT) {
                val towardTerminator = current + maxOf(1, (TERMINATOR_INT - current) / 2)
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = maxOf(current + 1, towardTerminator).toUByte(),
                )
            }

            if (current < UBYTE_MAX_INT) {
                val towardTop = current + maxOf(1, (UBYTE_MAX_INT - current) / 2)
                return buildSplicedModified(
                    source = source,
                    prefixEndExclusive = prefixEndExclusive,
                    tailStart = tailStart,
                    tailCopyLength = (index - tailStart) + 1,
                    modifiedTailIndex = index - tailStart,
                    modifiedValue = maxOf(current + 1, towardTop).toUByte(),
                )
            }
        }

        error("Invalid fractional index: missing valid increment point")
    }

    private fun buildSplicedCopy(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
        tailCopyLength: Int,
    ): UByteArray {
        val out = UByteArray(prefixEndExclusive + tailCopyLength + 1)

        if (prefixEndExclusive > 0) {
            source.copyInto(
                destination = out,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = prefixEndExclusive,
            )
        }

        if (tailCopyLength > 0) {
            source.copyInto(
                destination = out,
                destinationOffset = prefixEndExclusive,
                startIndex = tailStart,
                endIndex = tailStart + tailCopyLength,
            )
        }

        out[out.lastIndex] = TERMINATOR
        return out
    }

    private fun buildSplicedModified(
        source: UByteArray,
        prefixEndExclusive: Int,
        tailStart: Int,
        tailCopyLength: Int,
        modifiedTailIndex: Int,
        modifiedValue: UByte,
    ): UByteArray {
        val out = buildSplicedCopy(
            source = source,
            prefixEndExclusive = prefixEndExclusive,
            tailStart = tailStart,
            tailCopyLength = tailCopyLength,
        )
        out[prefixEndExclusive + modifiedTailIndex] = modifiedValue
        return out
    }

    private fun UByteArray.terminatedCopyOfRange(fromIndex: Int, toIndex: Int): UByteArray {
        val out = UByteArray((toIndex - fromIndex) + 1)
        this.copyInto(out, destinationOffset = 0, startIndex = fromIndex, endIndex = toIndex)
        out[out.lastIndex] = TERMINATOR
        return out
    }
}
