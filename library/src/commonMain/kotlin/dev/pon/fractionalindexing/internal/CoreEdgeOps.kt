package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndex.Companion.TERMINATOR

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.edgeInsert(
    index: FractionalIndex,
    minorStep: (UByteArray) -> UByteArray,
    boundaryMajor: Long,
    fallbackDelta: Long,
    overflowMessage: String,
): FractionalIndex {
    val sameMajorCandidate = minorStep(index.minor)

    if (index.major == boundaryMajor) {
        require(FractionalIndex.isEncodableMinorForMajor(index.major, sameMajorCandidate)) { overflowMessage }
        return FractionalIndex.fromMajorMinor(major = index.major, minor = sameMajorCandidate)
    }

    val fallbackMajor = index.major + fallbackDelta
    val fallbackMinor = defaultMinor()
    if (!FractionalIndex.isCompactMinor(sameMajorCandidate)) {
        return FractionalIndex.fromMajorMinor(major = fallbackMajor, minor = fallbackMinor)
    }

    val sameMajorLength = FractionalIndex.encodedLength(
        major = index.major,
        minorSize = sameMajorCandidate.size,
    )
    val fallbackLength = FractionalIndex.encodedLength(
        major = fallbackMajor,
        minorSize = fallbackMinor.size,
    )
    return if (sameMajorLength <= fallbackLength) {
        FractionalIndex.fromMajorMinor(major = index.major, minor = sameMajorCandidate)
    } else {
        FractionalIndex.fromMajorMinor(major = fallbackMajor, minor = fallbackMinor)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.beforeMinor(bytes: UByteArray): UByteArray {
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.beforeMinorSize(bytes: UByteArray): Int {
    for (i in bytes.indices) {
        val current = bytes[i].toInt()
        if (current > TERMINATOR_INT) return i + 1
        if (current > 0) return i + 2
    }
    error("Invalid fractional index: missing valid decrement point")
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.afterMinor(bytes: UByteArray): UByteArray {
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

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.afterMinorSize(bytes: UByteArray): Int {
    for (i in bytes.indices) {
        val current = bytes[i].toInt()
        if (current < TERMINATOR_INT) return i + 1
        if (current < UBYTE_MAX_INT) return i + 2
    }
    error("Invalid fractional index: missing valid increment point")
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.defaultMinor(): UByteArray = ubyteArrayOf(TERMINATOR)
