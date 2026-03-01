package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorBetweenDifferentMajorsTest {
    @Test
    fun between_adjacentMajors_staysInsideBounds() {
        val lower = FractionalIndex.default()
        val upper = FractionalIndexGenerator.after(lower)

        val mid = FractionalIndexGenerator.between(lower, upper).getOrThrow()

        assertTrue(lower < mid, "mid should be greater than lower")
        assertTrue(mid < upper, "mid should be smaller than upper")
    }

    @Test
    fun between_extremeOppositeMajors_doesNotOverflowAndStaysInside() {
        val lower = FractionalIndex.fromHexString("00800000000000000080").getOrThrow()
        val upper = FractionalIndex.fromHexString("ff7fffffffffffffff80").getOrThrow()

        val mid = FractionalIndexGenerator.between(lower, upper).getOrThrow()

        assertTrue(lower < mid, "mid should be greater than lower")
        assertTrue(mid < upper, "mid should be smaller than upper")
    }

    @Test
    fun growthProfile_staysWithinRegressionThresholds() {
        val checkpoints = listOf(300, 3_000, 10_000)
        val adjacent = runAdjacentPairPattern(checkpoints)
        val rootAnchored = runRootAnchoredPattern(checkpoints)

        val adjacentMaxBytesByCheckpoint = mapOf(
            300 to 47,
            3_000 to 432,
            10_000 to 1432,
        )
        val rootAnchoredMaxBytesByCheckpoint = mapOf(
            300 to 5,
            3_000 to 26,
            10_000 to 81,
        )

        for (checkpoint in checkpoints) {
            val adjacentActual = adjacent.getValue(checkpoint)
            val adjacentMax = adjacentMaxBytesByCheckpoint.getValue(checkpoint)
            assertTrue(
                adjacentActual <= adjacentMax,
                "Adjacent pattern regression at $checkpoint: actual=$adjacentActual max=$adjacentMax",
            )

            val rootActual = rootAnchored.getValue(checkpoint)
            val rootMax = rootAnchoredMaxBytesByCheckpoint.getValue(checkpoint)
            assertTrue(
                rootActual <= rootMax,
                "Root-anchored pattern regression at $checkpoint: actual=$rootActual max=$rootMax",
            )
        }
    }

    private fun runAdjacentPairPattern(
        checkpoints: List<Int>,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        var start = FractionalIndex.default()
        var end = FractionalIndexGenerator.after(start)

        for (i in 1..sorted.last()) {
            start = FractionalIndexGenerator.between(start, end).getOrThrow()
            end = FractionalIndexGenerator.between(start, end).getOrThrow()

            if (i in targets) {
                results[i] = start.bytes.size
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    private fun runRootAnchoredPattern(
        checkpoints: List<Int>,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        val start = FractionalIndex.default()
        var end = FractionalIndexGenerator.after(start)

        for (i in 1..sorted.last()) {
            end = FractionalIndexGenerator.between(start, end).getOrThrow()
            if (i in targets) {
                results[i] = end.bytes.size
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }
}
