package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.rebalanceKeysOrThrow(
    count: Int,
    lowerExclusive: FractionalIndex?,
    upperExclusive: FractionalIndex?,
): List<FractionalIndex> {
    require(count >= 0) { NON_NEGATIVE_COUNT_MESSAGE }
    if (count == 0) {
        return emptyList()
    }

    if (lowerExclusive == null) {
        if (upperExclusive == null) {
            return rebalanceUnbounded(count)
        }
        return rebalanceWithUpperBoundOnly(
            count = count,
            upperExclusive = upperExclusive,
        )
    }

    if (upperExclusive == null) {
        return rebalanceWithLowerBoundOnly(
            count = count,
            lowerExclusive = lowerExclusive,
        )
    }

    require(lowerExclusive != upperExclusive) { DISTINCT_BOUNDS_MESSAGE }
    val boundOrder = lowerExclusive.compareTo(upperExclusive)
    val lowerBound: FractionalIndex
    val upperBound: FractionalIndex
    if (boundOrder < 0) {
        lowerBound = lowerExclusive
        upperBound = upperExclusive
    } else {
        lowerBound = upperExclusive
        upperBound = lowerExclusive
    }

    return rebalanceWithinBounds(
        count = count,
        lowerExclusive = lowerBound,
        upperExclusive = upperBound,
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceUnbounded(
    count: Int,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = FractionalIndex.default()
    generated.add(current)

    repeat(count - 1) {
        current = after(current)
        generated.add(current)
    }

    return generated
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceWithLowerBoundOnly(
    count: Int,
    lowerExclusive: FractionalIndex,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = lowerExclusive
    repeat(count) {
        current = after(current)
        generated.add(current)
    }
    return generated
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceWithUpperBoundOnly(
    count: Int,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val generated = MutableList(count) { upperExclusive }
    var current = upperExclusive
    for (index in count - 1 downTo 0) {
        current = before(current)
        generated[index] = current
    }
    return generated
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceWithinBounds(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)

    fun appendBalancedKeys(
        remaining: Int,
        lower: FractionalIndex,
        upper: FractionalIndex,
    ) {
        if (remaining == 0) return

        val leftCount = remaining / 2
        val rightCount = remaining - leftCount - 1
        val center = betweenOrThrow(lower, upper)

        appendBalancedKeys(
            remaining = leftCount,
            lower = lower,
            upper = center,
        )
        generated.add(center)
        appendBalancedKeys(
            remaining = rightCount,
            lower = center,
            upper = upper,
        )
    }

    appendBalancedKeys(
        remaining = count,
        lower = lowerExclusive,
        upper = upperExclusive,
    )
    return generated
}
