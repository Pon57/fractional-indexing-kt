package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

@OptIn(ExperimentalUnsignedTypes::class)
internal object FractionalIndexGeneratorCore {
    internal const val DISTINCT_BOUNDS_MESSAGE = "bounds must be distinct"
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

    fun before(index: FractionalIndex): FractionalIndex = edgeInsert(
        index = index,
        minorStep = ::beforeMinor,
        boundaryMajor = MIN_MAJOR,
        fallbackDelta = -1L,
        overflowMessage = "major underflow",
    )

    fun after(index: FractionalIndex): FractionalIndex = edgeInsert(
        index = index,
        minorStep = ::afterMinor,
        boundaryMajor = MAX_MAJOR,
        fallbackDelta = 1L,
        overflowMessage = "major overflow",
    )

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
