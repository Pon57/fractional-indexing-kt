package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

@OptIn(ExperimentalUnsignedTypes::class)
internal object FractionalIndexGeneratorCore {
    internal const val DISTINCT_BOUNDS_MESSAGE = "bounds must be distinct"
    internal const val NON_NEGATIVE_COUNT_MESSAGE = "count must be non-negative"
    internal const val INVALID_ENDPOINT_ORDER_MESSAGE =
        "lowerEndpoint must be before upperEndpoint"
    internal const val INVALID_ENDPOINT_COUNT_RANGE_MESSAGE =
        "lowerEndpoint and upperEndpoint must define a valid range for count"
    internal const val INVALID_FORMAT_MESSAGE = "invalid fractional index format"
    internal const val INVALID_BOUNDS_MESSAGE = "lower bound must be smaller than upper bound"

    // chooseAdjacentSide tuning thresholds.
    // TIGHT_GAP_PRESSURE_THRESHOLD: when both sides' first minor byte is this far
    // from TERMINATOR (0x80), the gap is considered "tight" and we skip the
    // zero-major preference heuristic, falling through to length-based comparison.
    internal const val TIGHT_GAP_PRESSURE_THRESHOLD = 24

    // SPREAD_PRESSURE_GAIN_THRESHOLD: minimum pressure gain required to prefer
    // the spread variant over the minimal variant in adjacent-major selection.
    internal const val SPREAD_PRESSURE_GAIN_THRESHOLD = 32

    internal const val MIN_MAJOR: Long = Long.MIN_VALUE + 1
    internal const val MAX_MAJOR: Long = Long.MAX_VALUE

    internal val TERMINATOR_INT = FractionalIndex.TERMINATOR.toInt()
    internal val UBYTE_MAX_INT = UByte.MAX_VALUE.toInt()
    internal val DEFAULT_MINOR = ubyteArrayOf(FractionalIndex.TERMINATOR)

    fun before(index: FractionalIndex): FractionalIndex {
        if (index.major == 0L && index.minor.contentEquals(COMPACT_SUCCESSOR_MINOR)) {
            return FractionalIndex.default()
        }

        return edgeInsert(
            index = index,
            minorStep = ::beforeMinor,
            boundaryMajor = MIN_MAJOR,
            fallbackDelta = -1L,
            overflowMessage = "major underflow",
        )
    }

    fun after(index: FractionalIndex): FractionalIndex {
        if (index.major == 0L && index.minor.contentEquals(DEFAULT_MINOR)) {
            return FractionalIndex.fromMajorMinor(
                major = 0L,
                minor = COMPACT_SUCCESSOR_MINOR,
            )
        }

        return edgeInsert(
            index = index,
            minorStep = ::afterMinor,
            boundaryMajor = MAX_MAJOR,
            fallbackDelta = 1L,
            overflowMessage = "major overflow",
        )
    }

    fun between(
        first: FractionalIndex,
        second: FractionalIndex,
    ): Result<FractionalIndex> = runCatching { betweenOrThrow(first, second) }

    fun betweenOrThrow(
        first: FractionalIndex,
        second: FractionalIndex,
    ): FractionalIndex {
        val comparison = first.compareTo(second)
        require(comparison != 0) { DISTINCT_BOUNDS_MESSAGE }

        val left: FractionalIndex
        val right: FractionalIndex
        if (comparison < 0) {
            left = first
            right = second
        } else {
            left = second
            right = first
        }

        return when {
            hasNonAdjacentMajorGap(left.major, right.major) -> {
                val midMajor = midpointMajor(left.major, right.major)
                FractionalIndex.fromMajorMinor(major = midMajor, minor = DEFAULT_MINOR)
            }

            left.major < right.major -> {
                betweenAdjacentMajors(left, right)
            }

            else -> {
                // The compact successor of default() is [TERMINATOR, TERMINATOR].
                // Between before(default()) and after(default()), the 1-byte
                // default minor is the optimal midpoint that the general algorithm
                // cannot discover (it only produces results at lengths ≥ both bounds).
                if (left.major == 0L && isCompactSuccessorInterval(left.minor, right.minor)) {
                    return FractionalIndex.default()
                }

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

    fun rebalance(
        count: Int,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ): Result<List<FractionalIndex>> = runCatching {
        rebalanceOrThrow(
            count = count,
            lowerEndpoint = lowerEndpoint,
            upperEndpoint = upperEndpoint,
        )
    }

    fun rebalanceOrThrow(
        count: Int,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ): List<FractionalIndex> = rebalanceKeysOrThrow(
        count = count,
        lowerEndpoint = lowerEndpoint,
        upperEndpoint = upperEndpoint,
    )

    private val BEFORE_DEFAULT_MINOR = ubyteArrayOf(
        (FractionalIndex.TERMINATOR - 1u).toUByte(),
        FractionalIndex.TERMINATOR,
    )
    private val COMPACT_SUCCESSOR_MINOR = ubyteArrayOf(
        FractionalIndex.TERMINATOR,
        FractionalIndex.TERMINATOR,
    )

    private fun isCompactSuccessorInterval(left: UByteArray, right: UByteArray): Boolean {
        return left.contentEquals(BEFORE_DEFAULT_MINOR) && right.contentEquals(COMPACT_SUCCESSOR_MINOR)
    }
}
