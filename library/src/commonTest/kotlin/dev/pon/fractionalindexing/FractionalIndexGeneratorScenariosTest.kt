package dev.pon.fractionalindexing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorScenariosTest {
    @Test
    fun randomInsertion_preservesOrderAndInputImmutability() {
        val random = Random(0xFEEDBEEF.toInt())
        val ordered = mutableListOf(FractionalIndex.default())

        repeat(3_000) { step ->
            val insertAt = random.nextInt(ordered.size + 1)
            val generated = when {
                insertAt == 0 -> {
                    val right = ordered.first()
                    val rightSnapshot = right.toHexString()
                    val created = FractionalIndexGenerator.before(right)
                    assertEquals(rightSnapshot, right.toHexString(), "before() mutated right bound at step=$step")
                    created
                }

                insertAt == ordered.size -> {
                    val left = ordered.last()
                    val leftSnapshot = left.toHexString()
                    val created = FractionalIndexGenerator.after(left)
                    assertEquals(leftSnapshot, left.toHexString(), "after() mutated left bound at step=$step")
                    created
                }

                else -> {
                    val left = ordered[insertAt - 1]
                    val right = ordered[insertAt]
                    val leftSnapshot = left.toHexString()
                    val rightSnapshot = right.toHexString()
                    val created = if (random.nextBoolean()) {
                        FractionalIndexGenerator.between(left, right).getOrThrow()
                    } else {
                        FractionalIndexGenerator.between(right, left).getOrThrow()
                    }
                    assertEquals(leftSnapshot, left.toHexString(), "between() mutated left bound at step=$step")
                    assertEquals(rightSnapshot, right.toHexString(), "between() mutated right bound at step=$step")
                    created
                }
            }

            ordered.add(insertAt, generated)
            assertLocalOrderingAroundInsertion(ordered, insertAt, step)
        }

        assertStrictlyAscending(ordered, step = 3_000)
    }

    @Test
    fun after_growthProfile_plateausAtMediumMajorRange() {
        var current = FractionalIndex.default()
        val lengthsAtCheckpoints = mutableMapOf<Int, Int>()
        val checkpoints = listOf(100, 1_000, 4_000, 4_500)

        for (i in 1..checkpoints.last()) {
            current = FractionalIndexGenerator.after(current)
            if (i in checkpoints) {
                lengthsAtCheckpoints[i] = current.bytes.size
            }
        }

        // Short major range
        assertTrue(
            lengthsAtCheckpoints.getValue(100) <= 2,
            "Expected size <= 2 at 100 inserts, actual=${lengthsAtCheckpoints.getValue(100)}"
        )
        // Medium major range (Plateau)
        assertTrue(
            lengthsAtCheckpoints.getValue(1_000) <= 3,
            "Expected size <= 3 at 1,000 inserts, actual=${lengthsAtCheckpoints.getValue(1_000)}"
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_000) <= 3,
            "Expected size <= 3 at 4,000 inserts, actual=${lengthsAtCheckpoints.getValue(4_000)}"
        )
        // Spilled to extended major
        assertTrue(
            lengthsAtCheckpoints.getValue(4_500) > 3,
            "Expected size > 3 at 4,500 inserts, actual=${lengthsAtCheckpoints.getValue(4_500)}"
        )
    }

    @Test
    fun before_growthProfile_plateausAtMediumMajorRange() {
        var current = FractionalIndex.default()
        val lengthsAtCheckpoints = mutableMapOf<Int, Int>()
        val checkpoints = listOf(100, 1_000, 4_000, 4_500)

        for (i in 1..checkpoints.last()) {
            current = FractionalIndexGenerator.before(current)
            if (i in checkpoints) {
                lengthsAtCheckpoints[i] = current.bytes.size
            }
        }

        assertTrue(
            lengthsAtCheckpoints.getValue(100) <= 2,
            "Expected size <= 2 at 100 inserts, actual=${lengthsAtCheckpoints.getValue(100)}"
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(1_000) <= 3,
            "Expected size <= 3 at 1,000 inserts, actual=${lengthsAtCheckpoints.getValue(1_000)}"
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_000) <= 3,
            "Expected size <= 3 at 4,000 inserts, actual=${lengthsAtCheckpoints.getValue(4_000)}"
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_500) > 3,
            "Expected size > 3 at 4,500 inserts, actual=${lengthsAtCheckpoints.getValue(4_500)}"
        )
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
                "Adjacent pattern regression at $checkpoint: actual=$adjacentActual max=$adjacentMax"
            )

            val rootActual = rootAnchored.getValue(checkpoint)
            val rootMax = rootAnchoredMaxBytesByCheckpoint.getValue(checkpoint)
            assertTrue(
                rootActual <= rootMax,
                "Root-anchored pattern regression at $checkpoint: actual=$rootActual max=$rootMax"
            )
        }
    }

    @Test
    fun before_atNegativeMajorBoundary_usesSameMajorPathWhenAvailable() {
        // major = Long.MIN_VALUE + 1, minor = [0x80]
        val minMajor = FractionalIndex.fromHexString("00800000000000000080").getOrThrow()

        val generated = FractionalIndexGenerator.before(minMajor)

        assertTrue(generated < minMajor)
    }

    @Test
    fun after_atPositiveMajorBoundary_usesSameMajorPathWhenAvailable() {
        // major = Long.MAX_VALUE, minor = [0x80]
        val maxMajor = FractionalIndex.fromHexString("ff7fffffffffffffff80").getOrThrow()

        val generated = FractionalIndexGenerator.after(maxMajor)

        assertTrue(generated > maxMajor)
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
    fun between_sameMajorAtPositiveBoundary_staysInsideBounds() {
        val maxMajorRoot = FractionalIndex.fromHexString("ff7fffffffffffffff80").getOrThrow()
        val lower = FractionalIndexGenerator.after(maxMajorRoot)
        val upper = FractionalIndexGenerator.after(lower)

        val mid = FractionalIndexGenerator.between(lower, upper).getOrThrow()

        assertTrue(lower < mid, "mid should be greater than lower")
        assertTrue(mid < upper, "mid should be smaller than upper")
    }

    @Test
    fun between_withIdenticalBounds_returnsFailure() {
        val index = FractionalIndex.default()

        val result = FractionalIndexGenerator.between(index, index)

        assertTrue(
            result.isFailure,
            "Expected between() with identical bounds to fail"
        )
        assertEquals(
            "bounds must be distinct",
            result.exceptionOrNull()?.message
        )
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

    private fun assertStrictlyAscending(items: List<FractionalIndex>, step: Int) {
        for (i in 1 until items.size) {
            assertTrue(
                items[i - 1] < items[i],
                "Expected strict ascending order at step=$step index=$i left=${items[i - 1].toHexString()} right=${items[i].toHexString()}"
            )
        }
    }

    private fun assertLocalOrderingAroundInsertion(items: List<FractionalIndex>, insertedIndex: Int, step: Int) {
        if (insertedIndex > 0) {
            val left = items[insertedIndex - 1]
            val inserted = items[insertedIndex]
            assertTrue(
                left < inserted,
                "Expected left < inserted at step=$step index=$insertedIndex left=${left.toHexString()} inserted=${inserted.toHexString()}"
            )
        }
        if (insertedIndex < items.lastIndex) {
            val inserted = items[insertedIndex]
            val right = items[insertedIndex + 1]
            assertTrue(
                inserted < right,
                "Expected inserted < right at step=$step index=$insertedIndex inserted=${inserted.toHexString()} right=${right.toHexString()}"
            )
        }
    }
}
