package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex.Companion.TERMINATOR

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceBeforeMinimal(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceBeforeMinimalSize(
    source: UByteArray,
    tailStart: Int,
): Int {
    for (index in tailStart until source.size) {
        val current = source[index].toInt()
        if (current > TERMINATOR_INT) return index + 1
        if (current > 0) return index + 2
    }
    error("Invalid fractional index: missing valid decrement point")
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceAfterMinimal(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceAfterMinimalSize(
    source: UByteArray,
    tailStart: Int,
): Int {
    for (index in tailStart until source.size) {
        val current = source[index].toInt()
        if (current < TERMINATOR_INT) return index + 1
        if (current < UBYTE_MAX_INT) return index + 2
    }
    error("Invalid fractional index: missing valid increment point")
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceBeforeSpread(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.spliceAfterSpread(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.buildSplicedCopy(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.buildSplicedModified(
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun UByteArray.terminatedCopyOfRange(
    fromIndex: Int,
    toIndex: Int,
): UByteArray {
    val out = UByteArray((toIndex - fromIndex) + 1)
    this.copyInto(out, destinationOffset = 0, startIndex = fromIndex, endIndex = toIndex)
    out[out.lastIndex] = TERMINATOR
    return out
}
