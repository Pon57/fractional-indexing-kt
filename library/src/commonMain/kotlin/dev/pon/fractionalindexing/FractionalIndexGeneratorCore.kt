package dev.pon.fractionalindexing

import dev.pon.fractionalindexing.FractionalIndex.Companion.TERMINATOR
import kotlin.math.abs

@OptIn(ExperimentalUnsignedTypes::class)
internal object FractionalIndexGeneratorCore {
    private const val DISTINCT_BOUNDS_MESSAGE = "bounds must be distinct"
    private const val INVALID_FORMAT_MESSAGE = "invalid fractional index format"
    private const val INVALID_BOUNDS_MESSAGE = "lower bound must be smaller than upper bound"

    // chooseAdjacentSide tuning thresholds.
    // TIGHT_GAP_PRESSURE_THRESHOLD: when both sides' first minor byte is this far
    // from TERMINATOR (0x80), the gap is considered "tight" and we skip the
    // zero-major preference heuristic, falling through to length-based comparison.
    private const val TIGHT_GAP_PRESSURE_THRESHOLD = 24
    // SPREAD_PRESSURE_GAIN_THRESHOLD: minimum pressure gain required to prefer
    // the spread variant over the minimal variant in adjacent-major selection.
    private const val SPREAD_PRESSURE_GAIN_THRESHOLD = 32

    private const val MIN_MAJOR: Long = Long.MIN_VALUE + 1
    private const val MAX_MAJOR: Long = Long.MAX_VALUE

    private val TERMINATOR_INT = TERMINATOR.toInt()
    private val UBYTE_MAX_INT = UByte.MAX_VALUE.toInt()

    private data class CandidateScore(
        val projectedNextLength: Int,
        val currentLength: Int,
        val majorDistancePenalty: Int,
        val boundaryPressure: Int,
    ) : Comparable<CandidateScore> {
        override fun compareTo(other: CandidateScore): Int =
            compareValuesBy(
                this, other,
                { it.projectedNextLength },
                { it.currentLength },
                { it.majorDistancePenalty },
                { it.boundaryPressure },
            )
    }

    fun before(index: FractionalIndex): FractionalIndex =
        edgeInsert(index, ::beforeMinor, boundaryMajor = MIN_MAJOR, fallbackDelta = -1L, overflowMessage = "major underflow")

    fun after(index: FractionalIndex): FractionalIndex =
        edgeInsert(index, ::afterMinor, boundaryMajor = MAX_MAJOR, fallbackDelta = 1L, overflowMessage = "major overflow")

    private fun edgeInsert(
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

    fun between(
        first: FractionalIndex,
        second: FractionalIndex,
    ): Result<FractionalIndex> {
        return runCatching {
            val comparison = first.compareTo(second)
            require(comparison != 0) { DISTINCT_BOUNDS_MESSAGE }

            val (left, right) = if (comparison < 0) {
                first to second
            } else {
                second to first
            }

            when {
                hasNonAdjacentMajorGap(left.major, right.major) -> {
                    val midMajor = midpointMajor(left.major, right.major)
                    FractionalIndex.fromMajorMinor(major = midMajor, minor = defaultMinor())
                }

                left.major < right.major -> {
                    betweenAdjacentMajors(left, right)
                }

                else -> {
                    val minimal = minimalBetweenMinor(
                        left = left.minor,
                        right = right.minor,
                    )
                    val minorBetween = if (shouldFallbackToMinimal(left.minor, right.minor)) {
                        minimal
                    } else {
                        val spread = newBetweenSpreadBalanced(
                            major = left.major,
                            left = left.minor,
                            right = right.minor,
                        )
                        if (minimal.size < spread.size) minimal else spread
                    }
                    FractionalIndex.fromMajorMinor(major = left.major, minor = minorBetween)
                }
            }
        }
    }

    private fun betweenAdjacentMajors(
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

    private fun chooseAdjacentSide(
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

        val leftScore = scoreBetweenCandidate(
            leftMajor = left.major, leftMinor = left.minor,
            rightMajor = right.major, rightMinor = right.minor,
            candidateMajor = left.major, candidateMinor = leftMinorCandidate,
        )
        val rightScore = scoreBetweenCandidate(
            leftMajor = left.major, leftMinor = left.minor,
            rightMajor = right.major, rightMinor = right.minor,
            candidateMajor = right.major, candidateMinor = rightMinorCandidate,
        )

        return leftScore <= rightScore
    }

    private fun adjacentCandidate(
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

    private fun selectAdjacentMinorVariant(
        left: FractionalIndex,
        right: FractionalIndex,
        candidateMajor: Long,
        minimal: UByteArray,
        spread: UByteArray,
    ): UByteArray {
        val minimalScore = scoreBetweenCandidate(
            leftMajor = left.major, leftMinor = left.minor,
            rightMajor = right.major, rightMinor = right.minor,
            candidateMajor = candidateMajor, candidateMinor = minimal,
        )
        val spreadScore = scoreBetweenCandidate(
            leftMajor = left.major, leftMinor = left.minor,
            rightMajor = right.major, rightMinor = right.minor,
            candidateMajor = candidateMajor, candidateMinor = spread,
        )

        if (spreadScore.projectedNextLength < minimalScore.projectedNextLength) {
            return spread
        }
        if (spreadScore.projectedNextLength > minimalScore.projectedNextLength) {
            return minimal
        }
        if (spreadScore.currentLength < minimalScore.currentLength) {
            return spread
        }
        if (spreadScore.currentLength > minimalScore.currentLength) {
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

    private fun beforeMinor(bytes: UByteArray): UByteArray {
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

    private fun beforeMinorSize(bytes: UByteArray): Int {
        for (i in bytes.indices) {
            val current = bytes[i].toInt()
            if (current > TERMINATOR_INT) return i + 1
            if (current > 0) return i + 2
        }
        error("Invalid fractional index: missing valid decrement point")
    }

    private fun afterMinor(bytes: UByteArray): UByteArray {
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

    private fun afterMinorSize(bytes: UByteArray): Int {
        for (i in bytes.indices) {
            val current = bytes[i].toInt()
            if (current < TERMINATOR_INT) return i + 1
            if (current < UBYTE_MAX_INT) return i + 2
        }
        error("Invalid fractional index: missing valid increment point")
    }

    private fun shouldFallbackToMinimal(left: UByteArray, right: UByteArray): Boolean {
        if (left.size == 1 && left[0] == TERMINATOR) {
            return right.isNotEmpty() && right[0].toInt() == TERMINATOR_INT + 1
        }

        if (right.size == 1 && right[0] == TERMINATOR) {
            return left.isNotEmpty() && left[0].toInt() == TERMINATOR_INT - 1
        }

        return false
    }

    private fun newBetweenSpreadBalanced(
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

    private fun chooseSpreadCandidate(
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

        val leftScore = scoreBetweenCandidate(
            leftMajor = major, leftMinor = left,
            rightMajor = major, rightMinor = right,
            candidateMajor = major, candidateMinor = leftCandidate,
        )
        val rightScore = scoreBetweenCandidate(
            leftMajor = major, leftMinor = left,
            rightMajor = major, rightMinor = right,
            candidateMajor = major, candidateMinor = rightCandidate,
        )

        return if (leftScore <= rightScore) leftCandidate else rightCandidate
    }

    private fun candidatePressure(
        candidate: UByteArray,
        pivot: Int,
    ): Int {
        val maxNonTerminatorIndex = (candidate.lastIndex - 1).coerceAtLeast(0)
        val index = (pivot + 1).coerceIn(0, maxNonTerminatorIndex)
        return abs(candidate[index].toInt() - TERMINATOR_INT)
    }

    private fun scoreBetweenCandidate(
        leftMajor: Long,
        leftMinor: UByteArray,
        rightMajor: Long,
        rightMinor: UByteArray,
        candidateMajor: Long,
        candidateMinor: UByteArray,
    ): CandidateScore {
        val currentLength = FractionalIndex.encodedLength(candidateMajor, candidateMinor.size)
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
        val projectedNextLength = maxOf(nextLeftLength, nextRightLength)

        return CandidateScore(
            projectedNextLength = projectedNextLength,
            currentLength = currentLength,
            majorDistancePenalty = majorDistancePenalty(candidateMajor),
            boundaryPressure = boundaryPressure(candidateMinor),
        )
    }

    private fun estimateMinimalBetweenLength(
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
                    minLength = minOf(minLength, FractionalIndex.encodedLength(
                        major = leftMajor,
                        minorSize = afterMinorSize(leftMinor),
                    ))
                } else {
                    val leftCandidate = afterMinor(leftMinor)
                    if (FractionalIndex.isEncodableMinorForMajor(0L, leftCandidate)) {
                        minLength = minOf(minLength, leftCandidate.size)
                    }
                }

                if (rightMajor != 0L) {
                    minLength = minOf(minLength, FractionalIndex.encodedLength(
                        major = rightMajor,
                        minorSize = beforeMinorSize(rightMinor),
                    ))
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
                val minor = minimalBetweenMinor(
                    left = leftMinor,
                    right = rightMinor,
                )
                FractionalIndex.encodedLength(
                    major = leftMajor,
                    minorSize = minor.size,
                )
            }
        }
    }

    private fun hasNonAdjacentMajorGap(
        leftMajor: Long,
        rightMajor: Long,
    ): Boolean {
        if (leftMajor >= rightMajor) {
            return false
        }
        // major minimum is Long.MIN_VALUE + 1, so (rightMajor - 1) is always safe.
        return leftMajor < (rightMajor - 1L)
    }

    private fun midpointMajor(
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

    private fun boundaryPressure(minor: UByteArray): Int {
        return abs(minor.first().toInt() - TERMINATOR_INT)
    }

    private fun majorDistancePenalty(major: Long): Int =
        abs(major).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun minimalBetweenMinor(
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

    private fun defaultMinor(): UByteArray = ubyteArrayOf(TERMINATOR)
}
