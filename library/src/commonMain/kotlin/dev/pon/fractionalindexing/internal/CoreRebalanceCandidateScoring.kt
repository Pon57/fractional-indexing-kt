package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex

internal data class RebalanceProfileScore(
    val maxLength: Int,
    val totalLength: Long,
    val nextMaxLength: Int,
    val nextTotalLength: Long,
)

internal data class RebalanceGapKey(
    val lowerBound: FractionalIndex,
    val upperBound: FractionalIndex,
)

internal class RebalanceCandidateAccumulator(
    private val core: FractionalIndexGeneratorCore,
    private val lowerExclusive: FractionalIndex,
    private val upperExclusive: FractionalIndex,
) {
    private val immediateBetweenLengthCache = HashMap<RebalanceGapKey, Int>()
    private var best: List<FractionalIndex>? = null
    private var bestScore: RebalanceProfileScore? = null

    fun consider(candidate: List<FractionalIndex>?): RebalanceCandidateAccumulator {
        if (candidate == null) {
            return this
        }

        val currentBestScore = bestScore
        if (currentBestScore == null) {
            best = candidate
            bestScore = score(candidate)
        } else {
            val candidateScore = score(candidate)
            if (compareRebalanceProfileScores(currentBestScore, candidateScore) > 0) {
                best = candidate
                bestScore = candidateScore
            }
        }
        return this
    }

    fun bestOrNull(): List<FractionalIndex>? = best

    fun bestOrThrow(): List<FractionalIndex> =
        requireNotNull(best) { "candidates must not be empty" }

    private fun score(candidate: List<FractionalIndex>): RebalanceProfileScore =
        core.scoreRebalanceProfile(
            lowerExclusive = lowerExclusive,
            upperExclusive = upperExclusive,
            generated = candidate,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
}

internal fun encodedLength(index: FractionalIndex): Int =
    index.encodedLength

internal fun FractionalIndexGeneratorCore.newRebalanceCandidateAccumulator(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
): RebalanceCandidateAccumulator = RebalanceCandidateAccumulator(
    core = this,
    lowerExclusive = lowerExclusive,
    upperExclusive = upperExclusive,
)

internal inline fun FractionalIndexGeneratorCore.bestRebalanceCandidateOrThrow(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    block: RebalanceCandidateAccumulator.() -> Unit,
): List<FractionalIndex> = newRebalanceCandidateAccumulator(
    lowerExclusive = lowerExclusive,
    upperExclusive = upperExclusive,
).apply(block).bestOrThrow()

internal inline fun FractionalIndexGeneratorCore.bestRebalanceCandidateOrNull(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    block: RebalanceCandidateAccumulator.() -> Unit,
): List<FractionalIndex>? = newRebalanceCandidateAccumulator(
    lowerExclusive = lowerExclusive,
    upperExclusive = upperExclusive,
).apply(block).bestOrNull()

internal fun FractionalIndexGeneratorCore.scoreRebalanceProfile(
    lowerExclusive: FractionalIndex,
    upperExclusive: FractionalIndex,
    generated: List<FractionalIndex>,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>,
): RebalanceProfileScore {
    var maxLength = 0
    var totalLength = 0L
    generated.forEach { index ->
        val length = encodedLength(index)
        maxLength = maxOf(maxLength, length)
        totalLength += length.toLong()
    }

    var nextMaxLength = 0
    var nextTotalLength = 0L
    var previous = lowerExclusive
    generated.forEach { next ->
        val length = immediateBetweenLength(
            lowerBound = previous,
            upperBound = next,
            immediateBetweenLengthCache = immediateBetweenLengthCache,
        )
        nextMaxLength = maxOf(nextMaxLength, length)
        nextTotalLength += length.toLong()
        previous = next
    }
    val trailingLength = immediateBetweenLength(
        lowerBound = previous,
        upperBound = upperExclusive,
        immediateBetweenLengthCache = immediateBetweenLengthCache,
    )
    nextMaxLength = maxOf(nextMaxLength, trailingLength)
    nextTotalLength += trailingLength.toLong()

    return RebalanceProfileScore(
        maxLength = maxLength,
        totalLength = totalLength,
        nextMaxLength = nextMaxLength,
        nextTotalLength = nextTotalLength,
    )
}

internal fun compareRebalanceProfileScores(
    primary: RebalanceProfileScore,
    alternative: RebalanceProfileScore,
): Int {
    primary.maxLength.compareTo(alternative.maxLength).let { if (it != 0) return it }
    primary.totalLength.compareTo(alternative.totalLength).let { if (it != 0) return it }
    primary.nextMaxLength.compareTo(alternative.nextMaxLength).let { if (it != 0) return it }
    return primary.nextTotalLength.compareTo(alternative.nextTotalLength)
}

private fun FractionalIndexGeneratorCore.immediateBetweenLength(
    lowerBound: FractionalIndex,
    upperBound: FractionalIndex,
    immediateBetweenLengthCache: MutableMap<RebalanceGapKey, Int>,
): Int = immediateBetweenLengthCache.getOrPut(
    RebalanceGapKey(
        lowerBound = lowerBound,
        upperBound = upperBound,
    ),
) {
    val between = betweenOrThrow(lowerBound, upperBound)
    between.encodedLength
}
