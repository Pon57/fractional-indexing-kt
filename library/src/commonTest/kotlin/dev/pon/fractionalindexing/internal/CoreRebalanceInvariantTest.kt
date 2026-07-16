package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoreRebalanceInvariantTest {
    @Test
    fun emptyCandidateAccumulator_isInternalStateFailure() {
        val lower = FractionalIndex.default()
        val upper = FractionalIndexGenerator.after(lower)
        val accumulator = FractionalIndexGeneratorCore.newRebalanceCandidateAccumulator(
            lowerExclusive = lower,
            upperExclusive = upper,
        )

        assertFailsWith<IllegalStateException> {
            accumulator.bestOrThrow()
        }
    }
}
