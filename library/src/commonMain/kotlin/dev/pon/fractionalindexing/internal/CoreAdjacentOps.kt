package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import kotlin.math.abs

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.betweenAdjacentMajors(
    left: FractionalIndex,
    right: FractionalIndex,
): FractionalIndex {
    val leftMinorCandidate = adjacentCandidate(
        left = left,
        right = right,
        anchor = left,
        minorStep = ::afterMinor,
        spliceSpread = ::spliceAfterSpread,
    )
    val rightMinorCandidate = adjacentCandidate(
        left = left,
        right = right,
        anchor = right,
        minorStep = ::beforeMinor,
        spliceSpread = ::spliceBeforeSpread,
    )

    val leftAvailable = FractionalIndex.isEncodableMinorForMajor(left.major, leftMinorCandidate)
    val rightAvailable = FractionalIndex.isEncodableMinorForMajor(right.major, rightMinorCandidate)

    require(leftAvailable || rightAvailable) { INVALID_FORMAT_MESSAGE }

    val chooseLeft = when {
        leftAvailable && rightAvailable -> chooseAdjacentSide(
            left = left,
            right = right,
            leftMinorCandidate = leftMinorCandidate,
            rightMinorCandidate = rightMinorCandidate,
        )

        leftAvailable -> true
        else -> false
    }

    return if (chooseLeft) {
        FractionalIndex.fromMajorMinor(major = left.major, minor = leftMinorCandidate)
    } else {
        FractionalIndex.fromMajorMinor(major = right.major, minor = rightMinorCandidate)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.chooseAdjacentSide(
    left: FractionalIndex,
    right: FractionalIndex,
    leftMinorCandidate: UByteArray,
    rightMinorCandidate: UByteArray,
): Boolean {
    if ((left.major == 0L) != (right.major == 0L)) {
        val zeroOnLeft = left.major == 0L
        val zeroMinor = if (zeroOnLeft) left.minor else right.minor
        val nonZeroMinor = if (zeroOnLeft) right.minor else left.minor
        val zeroCandidate = if (zeroOnLeft) leftMinorCandidate else rightMinorCandidate
        val nonZeroCandidate = if (zeroOnLeft) rightMinorCandidate else leftMinorCandidate

        val zeroPressure = abs(zeroMinor.first().toInt() - TERMINATOR_INT)
        val nonZeroPressure = abs(nonZeroMinor.first().toInt() - TERMINATOR_INT)
        val isTightGap = zeroPressure >= TIGHT_GAP_PRESSURE_THRESHOLD && nonZeroPressure >= TIGHT_GAP_PRESSURE_THRESHOLD
        val keepZeroSide =
            !isTightGap &&
                zeroCandidate.size <= (nonZeroCandidate.size + 1) &&
                zeroMinor.size <= (nonZeroMinor.size + 2)
        if (keepZeroSide) {
            return zeroOnLeft
        }
    }

    val leftCandidateLength = FractionalIndex.encodedLength(
        major = left.major,
        minorSize = leftMinorCandidate.size,
    )
    val rightCandidateLength = FractionalIndex.encodedLength(
        major = right.major,
        minorSize = rightMinorCandidate.size,
    )
    if (leftCandidateLength != rightCandidateLength) {
        return leftCandidateLength < rightCandidateLength
    }

    val leftCurrentLength = FractionalIndex.encodedLength(
        major = left.major,
        minorSize = left.minor.size,
    )
    val rightCurrentLength = FractionalIndex.encodedLength(
        major = right.major,
        minorSize = right.minor.size,
    )
    if (leftCurrentLength != rightCurrentLength) {
        return leftCurrentLength < rightCurrentLength
    }

    val candidateSizeDiff = leftMinorCandidate.size - rightMinorCandidate.size
    if (candidateSizeDiff != 0) {
        return candidateSizeDiff < 0
    }

    val leftPressure = abs(left.minor.first().toInt() - TERMINATOR_INT)
    val rightPressure = abs(right.minor.first().toInt() - TERMINATOR_INT)
    if (leftPressure != rightPressure) {
        return leftPressure < rightPressure
    }

    return compareCandidateScores(
        leftMajor = left.major,
        leftMinor = left.minor,
        rightMajor = right.major,
        rightMinor = right.minor,
        candidateAMajor = left.major,
        candidateAMinor = leftMinorCandidate,
        candidateBMajor = right.major,
        candidateBMinor = rightMinorCandidate,
    ) <= 0
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.adjacentCandidate(
    left: FractionalIndex,
    right: FractionalIndex,
    anchor: FractionalIndex,
    minorStep: (UByteArray) -> UByteArray,
    spliceSpread: (UByteArray, Int, Int) -> UByteArray,
): UByteArray {
    val major = anchor.major
    val minor = anchor.minor
    val minimal = minorStep(minor)
    if (!FractionalIndex.isEncodableMinorForMajor(major, minimal)) {
        return minimal
    }

    val spread = spliceSpread(minor, 0, 0)
    if (!FractionalIndex.isEncodableMinorForMajor(major, spread)) {
        return minimal
    }

    return selectAdjacentMinorVariant(
        left = left,
        right = right,
        candidateMajor = major,
        minimal = minimal,
        spread = spread,
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.selectAdjacentMinorVariant(
    left: FractionalIndex,
    right: FractionalIndex,
    candidateMajor: Long,
    minimal: UByteArray,
    spread: UByteArray,
): UByteArray {
    val minimalProjected = candidateProjectedNextLength(
        leftMajor = left.major,
        leftMinor = left.minor,
        rightMajor = right.major,
        rightMinor = right.minor,
        candidateMajor = candidateMajor,
        candidateMinor = minimal,
    )
    val spreadProjected = candidateProjectedNextLength(
        leftMajor = left.major,
        leftMinor = left.minor,
        rightMajor = right.major,
        rightMinor = right.minor,
        candidateMajor = candidateMajor,
        candidateMinor = spread,
    )

    if (spreadProjected < minimalProjected) {
        return spread
    }
    if (spreadProjected > minimalProjected) {
        return minimal
    }

    val minimalCurrentLength = FractionalIndex.encodedLength(candidateMajor, minimal.size)
    val spreadCurrentLength = FractionalIndex.encodedLength(candidateMajor, spread.size)
    if (spreadCurrentLength < minimalCurrentLength) {
        return spread
    }
    if (spreadCurrentLength > minimalCurrentLength) {
        return minimal
    }

    val pressureGain = boundaryPressure(spread) - boundaryPressure(minimal)
    return when {
        spread.size < minimal.size -> spread
        spread.size > minimal.size -> minimal
        pressureGain >= SPREAD_PRESSURE_GAIN_THRESHOLD -> spread
        else -> minimal
    }
}
