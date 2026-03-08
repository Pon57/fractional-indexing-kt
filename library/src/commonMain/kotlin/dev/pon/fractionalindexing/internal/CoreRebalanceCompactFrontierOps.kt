@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

// The compact-frontier candidate enumerates whole profile alternatives, so keep it
// to the same tight-range budget as the recursive single-pivot path.
private const val ZERO_MAJOR_COMPACT_FRONTIER_CANDIDATE_THRESHOLD = 32

internal fun FractionalIndexGeneratorCore.rebalanceAroundZeroMajorCompactFrontierOrNull(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex>? {
    if (count > ZERO_MAJOR_COMPACT_FRONTIER_CANDIDATE_THRESHOLD) {
        return null
    }
    if (lowerExclusive.major != 0L || upperExclusive.major != 0L) {
        return null
    }

    val compactFrontier = buildZeroMajorCompactFrontierWithinBounds(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )
    if (compactFrontier.isEmpty() || compactFrontier.size >= count) {
        return null
    }

    // In tight zero-major windows, the shortest profile is usually:
    // consume every 1/2-byte compact key first, then place any remaining keys into the
    // gap that keeps the overall and next-insert profiles shortest.
    val compactFrontierCandidate = buildAugmentedCompactFrontierRebalance(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
        compactFrontier = compactFrontier,
    )
    val recursiveBalanced = buildBalancedFallbackRebalance(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )
    return bestRebalanceCandidateOrThrow(
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    ) {
        consider(compactFrontierCandidate)
        consider(recursiveBalanced)
    }
}

private fun FractionalIndexGeneratorCore.buildZeroMajorCompactFrontierWithinBounds(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): List<FractionalIndex> {
    val compactFrontier = ArrayList<FractionalIndex>(count)
    var current = lowerExclusive

    while (compactFrontier.size < count) {
        val next = after(current)
        if (!isShortZeroMajorCompactKey(next) || next >= upperExclusive) {
            break
        }
        compactFrontier.add(next)
        current = next
    }

    return compactFrontier
}

private fun FractionalIndexGeneratorCore.buildAugmentedCompactFrontierRebalance(
    count: Int,
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    compactFrontier: List<FractionalIndex>,
): List<FractionalIndex> {
    val generated = ArrayList<FractionalIndex>(count)
    generated.addAll(compactFrontier)
    // Share the between-length cache across augmentation steps so that unchanged
    // gaps (all gaps except the one that was just split) reuse prior results.
    val immediateBetweenLengthCache = HashMap<RebalanceGapKey, Int>()

    repeat(count - compactFrontier.size) {
        val insertion = chooseBestCompactFrontierInsertion(
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
            generated = generated,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        generated.add(insertion.insertIndex, insertion.candidate)
    }

    return generated
}

// --- Compact frontier insertion scoring ---

private data class CompactFrontierInsertion(
    val insertIndex: Int,
    val lowerBound: FractionalIndex,
    val upperBound: FractionalIndex,
    val candidate: FractionalIndex,
    val profile: List<FractionalIndex>,
)

private data class CompactFrontierGapPriority(
    val shortCompactBoundCount: Int,
    val maxBoundLength: Int,
    val candidateLength: Int,
)

private fun FractionalIndexGeneratorCore.chooseBestCompactFrontierInsertion(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    generated: List<FractionalIndex>,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>,
): CompactFrontierInsertion {
    var best: CompactFrontierInsertion? = null
    var bestGapPriority: CompactFrontierGapPriority? = null
    var bestProfileScore: RebalanceProfileScore? = null
    var previous = lowerExclusive

    for (insertIndex in 0..generated.size) {
        val next = if (insertIndex < generated.size) generated[insertIndex] else upperExclusive
        for (insertion in buildCompactFrontierInsertionsForGap(
            insertIndex = insertIndex,
            lowerBound = previous,
            upperBound = next,
            generated = generated,
        )) {
            val currentBest = best
            if (currentBest == null) {
                best = insertion
                bestGapPriority = compactFrontierGapPriority(insertion)
                bestProfileScore = null
                continue
            }

            val primaryGapPriority = compactFrontierGapPriority(insertion)
            val gapCmp = compareCompactFrontierGapPriority(
                primary = primaryGapPriority,
                alternative = bestGapPriority!!,
            )
            if (gapCmp > 0) continue
            if (gapCmp < 0) {
                best = insertion
                bestGapPriority = primaryGapPriority
                bestProfileScore = null
                continue
            }

            val primaryScore = scoreRebalanceProfile(
                lowerExclusive = lowerExclusive,
                upperExclusive = upperExclusive,
                generated = insertion.profile,
                immediateBetweenLengthCache = immediateBetweenLengthCache,
            )
            if (bestProfileScore == null) {
                bestProfileScore = scoreRebalanceProfile(
                    lowerExclusive = lowerExclusive,
                    upperExclusive = upperExclusive,
                    generated = currentBest.profile,
                    immediateBetweenLengthCache = immediateBetweenLengthCache,
                )
            }
            val profileCmp = compareRebalanceProfileScores(
                primary = primaryScore,
                alternative = bestProfileScore,
            )
            if (profileCmp > 0) continue
            if (profileCmp < 0) {
                best = insertion
                bestGapPriority = primaryGapPriority
                bestProfileScore = primaryScore
                continue
            }

            val candidateCmp = compareCandidateScores(
                leftMajor = lowerExclusive.major,
                leftMinor = lowerExclusive.minor,
                rightMajor = upperExclusive.major,
                rightMinor = upperExclusive.minor,
                candidateAMajor = insertion.candidate.major,
                candidateAMinor = insertion.candidate.minor,
                candidateBMajor = currentBest.candidate.major,
                candidateBMinor = currentBest.candidate.minor,
            )
            if (candidateCmp > 0) continue
            if (candidateCmp < 0) {
                best = insertion
                bestGapPriority = primaryGapPriority
                bestProfileScore = primaryScore
                continue
            }

            if (insertion.candidate < currentBest.candidate) {
                best = insertion
                bestGapPriority = primaryGapPriority
                bestProfileScore = primaryScore
            }
        }
        previous = next
    }

    return requireNotNull(best) { "no valid insertion found in compact frontier" }
}

private fun FractionalIndexGeneratorCore.buildCompactFrontierInsertionsForGap(
    insertIndex: Int,
    lowerBound: FractionalIndex,
    upperBound: FractionalIndex,
    generated: List<FractionalIndex>,
): List<CompactFrontierInsertion> {
    val candidates = ArrayList<FractionalIndex>(3)

    fun addCandidate(candidate: FractionalIndex) {
        if (candidate <= lowerBound || candidate >= upperBound) {
            return
        }
        if (candidates.none { it == candidate }) {
            candidates.add(candidate)
        }
    }

    addCandidate(betweenOrThrow(lowerBound, upperBound))
    addCandidate(before(upperBound))
    addCandidate(after(lowerBound))

    return candidates.map { candidate ->
        CompactFrontierInsertion(
            insertIndex = insertIndex,
            lowerBound = lowerBound,
            upperBound = upperBound,
            candidate = candidate,
            profile = ListWithInsertion(generated, insertIndex, candidate),
        )
    }
}

private fun compactFrontierGapPriority(
    insertion: CompactFrontierInsertion,
): CompactFrontierGapPriority {
    val lowerLength = encodedLength(insertion.lowerBound)
    val upperLength = encodedLength(insertion.upperBound)
    return CompactFrontierGapPriority(
        shortCompactBoundCount = shortCompactBoundCount(
            lowerBound = insertion.lowerBound,
            upperBound = insertion.upperBound,
        ),
        maxBoundLength = maxOf(lowerLength, upperLength),
        candidateLength = encodedLength(insertion.candidate),
    )
}

private fun compareCompactFrontierGapPriority(
    primary: CompactFrontierGapPriority,
    alternative: CompactFrontierGapPriority,
): Int {
    alternative.shortCompactBoundCount.compareTo(primary.shortCompactBoundCount)
        .let { if (it != 0) return it }
    primary.maxBoundLength.compareTo(alternative.maxBoundLength)
        .let { if (it != 0) return it }
    return primary.candidateLength.compareTo(alternative.candidateLength)
}

// Read-only view of [base] with [element] logically inserted at [insertIndex].
// Avoids copying the entire list for each candidate profile.
private class ListWithInsertion<T>(
    private val base: List<T>,
    private val insertIndex: Int,
    private val element: T,
) : AbstractList<T>() {
    override val size: Int get() = base.size + 1
    override fun get(index: Int): T = when {
        index < insertIndex -> base[index]
        index == insertIndex -> element
        else -> base[index - 1]
    }
}

private fun shortCompactBoundCount(
    lowerBound: FractionalIndex,
    upperBound: FractionalIndex,
): Int =
    (if (isShortZeroMajorCompactKey(lowerBound)) 1 else 0) +
        (if (isShortZeroMajorCompactKey(upperBound)) 1 else 0)
