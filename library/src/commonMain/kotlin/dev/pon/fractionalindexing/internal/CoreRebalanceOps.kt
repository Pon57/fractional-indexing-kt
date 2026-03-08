package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

@OptIn(ExperimentalUnsignedTypes::class)
internal fun FractionalIndexGeneratorCore.rebalanceKeysOrThrow(
    count: Int,
    lowerEndpoint: FractionalIndex?,
    upperEndpoint: FractionalIndex?,
): List<FractionalIndex> {
    require(count >= 0) { NON_NEGATIVE_COUNT_MESSAGE }
    if (count == 0) {
        return emptyList()
    }

    if (lowerEndpoint == null) {
        if (upperEndpoint == null) {
            return rebalanceUnbounded(count)
        }
        return rebalanceWithUpperEndpointOnly(
            count = count,
            upperEndpoint = upperEndpoint,
        )
    }

    if (upperEndpoint == null) {
        return rebalanceWithLowerEndpointOnly(
            count = count,
            lowerEndpoint = lowerEndpoint,
        )
    }

    val endpointOrder = lowerEndpoint.compareTo(upperEndpoint)
    return when {
        endpointOrder > 0 -> throw IllegalArgumentException(INVALID_ENDPOINT_ORDER_MESSAGE)
        endpointOrder == 0 -> {
            require(count == 1) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
            listOf(lowerEndpoint)
        }

        else -> {
            require(count >= 2) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
            rebalanceWithinEndpoints(
                count = count,
                lowerEndpoint = lowerEndpoint,
                upperEndpoint = upperEndpoint,
            )
        }
    }
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
private fun FractionalIndexGeneratorCore.rebalanceWithLowerEndpointOnly(
    count: Int,
    lowerEndpoint: FractionalIndex,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = lowerEndpoint
    generated.add(current)

    repeat(count - 1) {
        current = after(current)
        generated.add(current)
    }
    return generated
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceWithUpperEndpointOnly(
    count: Int,
    upperEndpoint: FractionalIndex,
): List<FractionalIndex> {
    val generated = MutableList(count) { upperEndpoint }
    var current = upperEndpoint
    for (index in count - 2 downTo 0) {
        current = before(current)
        generated[index] = current
    }
    return generated
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun FractionalIndexGeneratorCore.rebalanceWithinEndpoints(
    count: Int,
    lowerEndpoint: FractionalIndex,
    upperEndpoint: FractionalIndex,
): List<FractionalIndex> {
    if (count == 2) {
        return listOf(lowerEndpoint, upperEndpoint)
    }

    val generated = ArrayList<FractionalIndex>(count)
    generated.add(lowerEndpoint)

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
        remaining = count - 2,
        lower = lowerEndpoint,
        upper = upperEndpoint,
    )
    generated.add(upperEndpoint)
    return generated
}
