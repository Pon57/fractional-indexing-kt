package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

// Maximum recursion depth for the optimization pipeline. When exceeded,
// rebalanceWithinExclusiveBounds skips optimization and falls back to balanced
// binary splitting, which has O(log count) depth on its own and does not
// re-enter the pipeline.
//
// Depth consumption varies by strategy:
//  - Binary pivot splits (buildFixedPivotRebalanceCandidate): O(log count),
//    each side increments depth by 1 and halves the remaining count.
//  - Linear boundary extension (rebalanceFromExtendedLowerBoundaryOrNull):
//    O(depth) — each call consumes one key and increments depth by 1, so
//    the limit directly caps how many extensions can chain before fallback.
//
// 12 covers binary splits up to count ≈ 4096 and allows up to 12 chained
// boundary extensions before forcing the balanced fallback.
private const val MAX_REBALANCE_OPTIMIZATION_DEPTH = 12

private data class FallbackRebalanceState(
    val remaining: Int,
    val lower: FractionalIndex,
    val upper: FractionalIndex,
)

// A fallback candidate is kept as a tree with composable profile aggregates.
// This lets every recursive state compare the same scores as the flattened-list
// implementation while only materializing the winning root plan once.
private sealed interface FallbackRebalancePlan {
    val count: Int
    val firstOrNull: FractionalIndex?
    val lastOrNull: FractionalIndex?
    val maxLength: Int
    val totalLength: Long
    val internalNextMaxLength: Int
    val internalNextTotalLength: Long

    fun appendTo(destination: MutableList<FractionalIndex>)
}

private object EmptyFallbackRebalancePlan : FallbackRebalancePlan {
    override val count: Int = 0
    override val firstOrNull: FractionalIndex? = null
    override val lastOrNull: FractionalIndex? = null
    override val maxLength: Int = 0
    override val totalLength: Long = 0L
    override val internalNextMaxLength: Int = 0
    override val internalNextTotalLength: Long = 0L

    override fun appendTo(destination: MutableList<FractionalIndex>) = Unit
}

private class SingletonFallbackRebalancePlan(
    private val index: FractionalIndex,
) : FallbackRebalancePlan {
    override val count: Int = 1
    override val firstOrNull: FractionalIndex = index
    override val lastOrNull: FractionalIndex = index
    override val maxLength: Int = index.encodedLength
    override val totalLength: Long = index.encodedLength.toLong()
    override val internalNextMaxLength: Int = 0
    override val internalNextTotalLength: Long = 0L

    override fun appendTo(destination: MutableList<FractionalIndex>) {
        destination.add(index)
    }
}

private class CompositeFallbackRebalancePlan(
    private val left: FallbackRebalancePlan,
    private val centerOrNull: FractionalIndex?,
    private val right: FallbackRebalancePlan,
    override val count: Int,
    override val firstOrNull: FractionalIndex,
    override val lastOrNull: FractionalIndex,
    override val maxLength: Int,
    override val totalLength: Long,
    override val internalNextMaxLength: Int,
    override val internalNextTotalLength: Long,
) : FallbackRebalancePlan {
    override fun appendTo(destination: MutableList<FractionalIndex>) {
        left.appendTo(destination)
        if (centerOrNull != null) {
            destination.add(centerOrNull)
        }
        right.appendTo(destination)
    }
}

private class FallbackRebalancePlanAccumulator(
    private val core: FractionalIndexGeneratorCore,
    private val lowerExclusive: FractionalIndex,
    private val upperExclusive: FractionalIndex,
    private val immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>,
) {
    private var bestPlan: FallbackRebalancePlan? = null
    private var bestScore: RebalanceProfileScore? = null

    fun consider(plan: FallbackRebalancePlan) {
        val score = core.scoreFallbackRebalancePlan(
            plan = plan,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        val currentBestScore = bestScore
        if (currentBestScore == null || compareRebalanceProfileScores(currentBestScore, score) > 0) {
            bestPlan = plan
            bestScore = score
        }
    }

    fun bestOrThrow(): FallbackRebalancePlan =
        checkNotNull(bestPlan) { "fallback candidates must not be empty" }
}

internal fun FractionalIndexGeneratorCore.rebalanceKeysOrThrow(
    count: Int,
    lowerEndpoint: FractionalIndex?,
    upperEndpoint: FractionalIndex?,
): List<FractionalIndex> {
    validateRebalanceArgumentsOrThrow(
        count = count,
        lowerEndpoint = lowerEndpoint,
        upperEndpoint = upperEndpoint,
    )
    if (count == 0) {
        return emptyList()
    }

    if (lowerEndpoint == null) {
        return if (upperEndpoint == null) {
            rebalanceUnbounded(count)
        } else {
            rebalanceWithUpperEndpointOnly(count = count, upperEndpoint = upperEndpoint)
        }
    }
    if (upperEndpoint == null) {
        return rebalanceWithLowerEndpointOnly(count = count, lowerEndpoint = lowerEndpoint)
    }

    if (lowerEndpoint.compareTo(upperEndpoint) == 0) {
        return listOf(lowerEndpoint)
    }
    return rebalanceWithinEndpoints(
        count = count,
        lowerEndpoint = lowerEndpoint,
        upperEndpoint = upperEndpoint,
    )
}

internal fun FractionalIndexGeneratorCore.validateRebalanceArgumentsOrThrow(
    count: Int,
    lowerEndpoint: FractionalIndex?,
    upperEndpoint: FractionalIndex?,
) {
    require(count >= 0) { NON_NEGATIVE_COUNT_MESSAGE }
    if (count == 0 || lowerEndpoint == null || upperEndpoint == null) return

    val endpointOrder = lowerEndpoint.compareTo(upperEndpoint)
    when {
        endpointOrder > 0 -> throw IllegalArgumentException(INVALID_ENDPOINT_ORDER_MESSAGE)
        endpointOrder == 0 -> require(count == 1) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
        else -> require(count >= 2) { INVALID_ENDPOINT_COUNT_RANGE_MESSAGE }
    }
}

private fun FractionalIndexGeneratorCore.rebalanceUnbounded(
    count: Int,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    var current = DEFAULT_INDEX
    generated.add(current)

    repeat(count - 1) {
        current = after(current)
        generated.add(current)
    }

    return generated
}

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

private fun FractionalIndexGeneratorCore.rebalanceWithinEndpoints(
    count: Int,
    lowerEndpoint: FractionalIndex,
    upperEndpoint: FractionalIndex,
): List<FractionalIndex> {
    if (count == 2) {
        return listOf(lowerEndpoint, upperEndpoint)
    }

    val interior = rebalanceWithinExclusiveBounds(
        count = count - 2,
        lowerExclusive = lowerEndpoint,
        upperExclusive = upperEndpoint,
    )
    return ArrayList<FractionalIndex>(count).apply {
        add(lowerEndpoint)
        addAll(interior)
        add(upperEndpoint)
    }
}

internal fun FractionalIndexGeneratorCore.rebalanceWithinExclusiveBounds(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int = 0,
): List<FractionalIndex> {
    if (count == 0) {
        return emptyList()
    }

    if (depth < MAX_REBALANCE_OPTIMIZATION_DEPTH) {
        val optimized = rebalanceWithinExclusiveBoundsOptimizedOrNull(
            count = count,
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
            depth = depth,
        )
        if (optimized != null) {
            if (count > RebalanceThresholds.OPTIMIZED_VS_BALANCED_FALLBACK) {
                return optimized
            }
            return bestRebalanceCandidateOrThrow(
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
            ) {
                consider(optimized)
                consider(
                    buildBalancedFallbackRebalance(
                        count = count,
                        lowerExclusive = lowerExclusive,
                        upperExclusive = upperExclusive,
                    ),
                )
            }
        }
    }

    return buildBalancedFallbackRebalance(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )
}

// Optimization pipeline: strategies are tried in order from broadest to narrowest scope.
// Each returns null when its preconditions are not met, falling through to the next.
//
// 1. Major gap        — different majors; can distribute keys across whole major slots
// 2. Single byte pivot — same major, adjacent byte gap; direct balanced split
// 3. Compact frontier  — zero-major only (count ≤ RebalanceThresholds.ZERO_MAJOR_COMPACT_FRONTIER_CANDIDATE); greedily consumes short compact
//                        keys before minor gap, which cannot see these cross-length slots
// 4. Minor gap         — same major, same length; evenly spaced or terminator pivot split
// 5. Length boundary    — same major, different minor lengths; fills the boundary gap
private fun FractionalIndexGeneratorCore.rebalanceWithinExclusiveBoundsOptimizedOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    depth: Int,
): List<FractionalIndex>? {
    rebalanceAcrossMajorGapOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAroundSingleBytePivotOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAroundZeroMajorCompactFrontierOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )?.let { return it }

    rebalanceAcrossMinorGapOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    rebalanceAcrossLengthBoundaryOrNull(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        depth = depth,
    )?.let { return it }

    return null
}

internal fun FractionalIndexGeneratorCore.buildBalancedFallbackRebalance(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val cache = HashMap<FallbackRebalanceState, FallbackRebalancePlan>()

    fun buildBalancedPlan(
        remaining: Int,
        lower: FractionalIndex,
        upper: FractionalIndex,
    ): FallbackRebalancePlan = cache.getOrPut(
        FallbackRebalanceState(
            remaining = remaining,
            lower = lower,
            upper = upper,
        ),
    ) {
        if (remaining == 0) {
            return@getOrPut EmptyFallbackRebalancePlan
        }

        val center = betweenOrThrow(lower, upper)
        if (remaining == 1) {
            return@getOrPut SingletonFallbackRebalancePlan(center)
        }

        fun buildCandidate(
            leftCount: Int,
            includeCenter: Boolean,
            immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>?,
        ): FallbackRebalancePlan {
            val rightCount = remaining - leftCount - if (includeCenter) 1 else 0
            return buildCompositeFallbackRebalancePlan(
                left = buildBalancedPlan(leftCount, lower, center),
                centerOrNull = center.takeIf { includeCenter },
                right = buildBalancedPlan(rightCount, center, upper),
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            )
        }

        val leftCount = remaining / 2
        if ((remaining % 2) != 0) {
            return@getOrPut buildCandidate(
                leftCount = leftCount,
                includeCenter = true,
                immediateBetweenLengthCache = null,
            )
        }

        val immediateBetweenLengthCache = HashMap<RebalanceGapKey, Int>()
        val candidates = FallbackRebalancePlanAccumulator(
            core = this,
            lowerExclusive = lower,
            upperExclusive = upper,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        candidates.consider(
            buildCandidate(
                leftCount = leftCount,
                includeCenter = false,
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            ),
        )
        candidates.consider(
            buildCandidate(
                leftCount = leftCount,
                includeCenter = true,
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            ),
        )
        candidates.consider(
            buildCandidate(
                leftCount = leftCount - 1,
                includeCenter = true,
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            ),
        )
        candidates.bestOrThrow()
    }

    val plan = buildBalancedPlan(
        remaining = count,
        lower = lowerExclusive,
        upper = upperExclusive,
    )
    check(plan.count == count) { "fallback plan count must match requested count" }
    // Plans explored only for scoring are no longer needed while the winner is materialized.
    cache.clear()
    return ArrayList<FractionalIndex>(count).apply {
        plan.appendTo(this)
    }
}

private fun FractionalIndexGeneratorCore.buildCompositeFallbackRebalancePlan(
    left: FallbackRebalancePlan,
    centerOrNull: FractionalIndex?,
    right: FallbackRebalancePlan,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>?,
): FallbackRebalancePlan {
    val first = left.firstOrNull ?: centerOrNull ?: right.firstOrNull
    val last = right.lastOrNull ?: centerOrNull ?: left.lastOrNull
    checkNotNull(first) { "composite fallback plan must not be empty" }
    checkNotNull(last) { "composite fallback plan must not be empty" }

    var internalNextMaxLength = maxOf(
        left.internalNextMaxLength,
        right.internalNextMaxLength,
    )
    var internalNextTotalLength =
        left.internalNextTotalLength + right.internalNextTotalLength
    var previous = left.lastOrNull

    if (centerOrNull != null) {
        val previousIndex = previous
        if (previousIndex != null) {
            val length = fallbackImmediateBetweenLength(
                lowerBound = previousIndex,
                upperBound = centerOrNull,
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            )
            internalNextMaxLength = maxOf(internalNextMaxLength, length)
            internalNextTotalLength += length.toLong()
        }
        previous = centerOrNull
    }

    val rightFirst = right.firstOrNull
    if (previous != null && rightFirst != null) {
        val length = fallbackImmediateBetweenLength(
            lowerBound = previous,
            upperBound = rightFirst,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        internalNextMaxLength = maxOf(internalNextMaxLength, length)
        internalNextTotalLength += length.toLong()
    }

    val centerLength = centerOrNull?.encodedLength ?: 0
    return CompositeFallbackRebalancePlan(
        left = left,
        centerOrNull = centerOrNull,
        right = right,
        count = left.count + right.count + if (centerOrNull == null) 0 else 1,
        firstOrNull = first,
        lastOrNull = last,
        maxLength = maxOf(left.maxLength, centerLength, right.maxLength),
        totalLength = left.totalLength + centerLength.toLong() + right.totalLength,
        internalNextMaxLength = internalNextMaxLength,
        internalNextTotalLength = internalNextTotalLength,
    )
}

private fun FractionalIndexGeneratorCore.scoreFallbackRebalancePlan(
    plan: FallbackRebalancePlan,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>,
): RebalanceProfileScore {
    val first = plan.firstOrNull
    if (first == null) {
        val nextLength = immediateBetweenLength(
            lowerBound = lowerExclusive,
            upperBound = upperExclusive,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        return RebalanceProfileScore(
            maxLength = 0,
            totalLength = 0L,
            nextMaxLength = nextLength,
            nextTotalLength = nextLength.toLong(),
        )
    }

    val leadingLength = immediateBetweenLength(
        lowerBound = lowerExclusive,
        upperBound = first,
        immediateBetweenLengthCache = immediateBetweenLengthCache,
    )
    val trailingLength = immediateBetweenLength(
        lowerBound = checkNotNull(plan.lastOrNull),
        upperBound = upperExclusive,
        immediateBetweenLengthCache = immediateBetweenLengthCache,
    )
    return RebalanceProfileScore(
        maxLength = plan.maxLength,
        totalLength = plan.totalLength,
        nextMaxLength = maxOf(
            plan.internalNextMaxLength,
            leadingLength,
            trailingLength,
        ),
        nextTotalLength = plan.internalNextTotalLength +
            leadingLength.toLong() +
            trailingLength.toLong(),
    )
}

private fun FractionalIndexGeneratorCore.fallbackImmediateBetweenLength(
    lowerBound: FractionalIndex,
    upperBound: FractionalIndex,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>?,
): Int {
    return if (immediateBetweenLengthCache == null) {
        betweenOrThrow(lowerBound, upperBound).encodedLength
    } else {
        immediateBetweenLength(
            lowerBound = lowerBound,
            upperBound = upperBound,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
    }
}
