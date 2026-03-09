package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

// Number of distinct after-ranks that fit in a single suffix byte (TERMINATOR+1 .. 0xFF).
// TERMINATOR is 0x80 (128), so 0xFF - 0x80 = 127.
private const val AFTER_RANK_STRIDE = 127

// Number of distinct before-ranks that fit in a single suffix byte (0x00 .. TERMINATOR-1).
// TERMINATOR is 0x80 (128).
private const val BEFORE_RANK_STRIDE = 128

// Rebalance builders allocate fresh minor arrays, so this helper can centralize
// the zero-copy compact-zero-major fast path.
internal fun fractionalIndexFromOwnedRebalanceMinor(
    major: Long,
    minor: UByteArray,
): FractionalIndex {
    return if (major == 0L && FractionalIndex.isCompactMinor(minor)) {
        FractionalIndex.fromCompactMinorUnsafe(minor)
    } else {
        FractionalIndex.fromMajorMinor(major, minor)
    }
}

internal fun isShortZeroMajorCompactKey(index: FractionalIndex): Boolean =
    index.major == 0L && index.minor.size <= 2

internal fun extendMinorWithTerminator(
    minor: UByteArray,
): UByteArray {
    val extended = UByteArray(minor.size + 1)
    minor.copyInto(extended)
    extended[extended.lastIndex] = FractionalIndex.TERMINATOR
    return extended
}

internal fun buildMinorAtPivot(
    prefix: UByteArray,
    prefixLength: Int,
    pivotByte: UByte,
): UByteArray {
    val minor = UByteArray(
        if (pivotByte == FractionalIndex.TERMINATOR) {
            prefixLength + 1
        } else {
            prefixLength + 2
        },
    )
    if (prefixLength > 0) {
        prefix.copyInto(
            destination = minor,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = prefixLength,
        )
    }
    minor[prefixLength] = pivotByte
    minor[minor.lastIndex] = FractionalIndex.TERMINATOR
    return minor
}

internal fun buildPivotMinorWithAfterRank(
    prefix: UByteArray,
    prefixLength: Int,
    pivotByte: UByte,
    rank: Int,
): UByteArray {
    require(rank > 0) { "rank must be positive" }

    val suffixRepeatCount = (rank - 1) / AFTER_RANK_STRIDE
    val terminatorInt = FractionalIndex.TERMINATOR.toInt()
    val suffixTerminalByte = (terminatorInt + (((rank - 1) % AFTER_RANK_STRIDE) + 1)).toUByte()
    val suffixLength = suffixRepeatCount + 2
    val minor = UByteArray(prefixLength + 1 + suffixLength)
    if (prefixLength > 0) {
        prefix.copyInto(
            destination = minor,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = prefixLength,
        )
    }
    minor[prefixLength] = pivotByte
    val suffixStart = prefixLength + 1
    for (index in 0 until suffixRepeatCount) {
        minor[suffixStart + index] = UByte.MAX_VALUE
    }
    minor[suffixStart + suffixRepeatCount] = suffixTerminalByte
    minor[minor.lastIndex] = FractionalIndex.TERMINATOR
    return minor
}

internal fun buildPivotMinorWithBeforeRank(
    prefix: UByteArray,
    prefixLength: Int,
    pivotByte: UByte,
    rank: Int,
): UByteArray {
    require(rank > 0) { "rank must be positive" }

    val suffixRepeatCount = (rank - 1) / BEFORE_RANK_STRIDE
    val terminatorInt = FractionalIndex.TERMINATOR.toInt()
    val suffixTerminalByte = (terminatorInt - (((rank - 1) % BEFORE_RANK_STRIDE) + 1)).toUByte()
    val suffixLength = suffixRepeatCount + 2
    val minor = UByteArray(prefixLength + 1 + suffixLength)
    if (prefixLength > 0) {
        prefix.copyInto(
            destination = minor,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = prefixLength,
        )
    }
    minor[prefixLength] = pivotByte
    val suffixStart = prefixLength + 1
    for (index in 0 until suffixRepeatCount) {
        minor[suffixStart + index] = 0u
    }
    minor[suffixStart + suffixRepeatCount] = suffixTerminalByte
    minor[minor.lastIndex] = FractionalIndex.TERMINATOR
    return minor
}
