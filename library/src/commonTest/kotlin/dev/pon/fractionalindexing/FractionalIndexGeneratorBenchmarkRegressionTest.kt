package dev.pon.fractionalindexing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorBenchmarkRegressionTest {
    private data class LengthStats(
        val total: Int,
        val p95: Int,
        val max: Int,
    )

    private data class MoveSelection(
        val from: Int,
        val to: Int,
    )

    @Test
    fun growthProfile_snapshot_rawAndBase64TransportLength() {
        val checkpoints = listOf(300, 3_000, 10_000)

        val adjacentRaw = runAdjacentPairPattern(checkpoints) { it.bytes.size }
        val rootAnchoredRaw = runRootAnchoredPattern(checkpoints) { it.bytes.size }
        val adjacentBase64 = runAdjacentPairPattern(checkpoints) { it.toBase64String().length }
        val rootAnchoredBase64 = runRootAnchoredPattern(checkpoints) { it.toBase64String().length }

        assertEquals(
            linkedMapOf(300 to 47, 3_000 to 432, 10_000 to 1432),
            adjacentRaw,
            "Adjacent pattern raw-byte snapshot changed. If intentional, update this baseline together with benchmark notes.",
        )
        assertEquals(
            linkedMapOf(300 to 5, 3_000 to 26, 10_000 to 81),
            rootAnchoredRaw,
            "Root-anchored pattern raw-byte snapshot changed. If intentional, update this baseline together with benchmark notes.",
        )
        assertEquals(
            linkedMapOf(300 to 64, 3_000 to 576, 10_000 to 1912),
            adjacentBase64,
            "Adjacent pattern Base64-length snapshot changed. If intentional, update this baseline together with benchmark notes.",
        )
        assertEquals(
            linkedMapOf(300 to 8, 3_000 to 36, 10_000 to 108),
            rootAnchoredBase64,
            "Root-anchored pattern Base64-length snapshot changed. If intentional, update this baseline together with benchmark notes.",
        )
    }

    @Test
    fun edgeGrowth_after_snapshot_atMajorBoundaryTransition() {
        val checkpoints = listOf(100, 1_000, 4_000, 4_500)
        val actual = runEdgeGrowthPattern(checkpoints) { current ->
            FractionalIndexGenerator.after(current)
        }

        assertEquals(
            linkedMapOf(100 to 2, 1_000 to 3, 4_000 to 3, 4_500 to 4),
            actual,
            "after() edge-growth snapshot changed. actual=$actual",
        )
    }

    @Test
    fun edgeGrowth_before_snapshot_atMajorBoundaryTransition() {
        val checkpoints = listOf(100, 1_000, 4_000, 4_500)
        val actual = runEdgeGrowthPattern(checkpoints) { current ->
            FractionalIndexGenerator.before(current)
        }

        assertEquals(
            linkedMapOf(100 to 2, 1_000 to 3, 4_000 to 3, 4_500 to 4),
            actual,
            "before() edge-growth snapshot changed. actual=$actual",
        )
    }

    @Test
    fun smallInwardMove_averageGeneratedRawLength_snapshot() {
        val average = runSmallInwardMoveAverageLength(
            steps = 240,
            initialSize = 64,
        )

        assertEquals(
            10.125,
            average,
            absoluteTolerance = 1e-9,
            message = "Small inward-move average generated raw-byte length snapshot changed. If intentional, update this baseline.",
        )
    }

    @Test
    fun insert_uniformRandom_rawLengthDistribution_snapshot() {
        val actual = runInsertPatternStats(
            initialSize = 64,
            steps = 2_500,
            seed = 7,
            insertSelector = { size, _, random -> random.nextInt(size + 1) },
            measure = { it.bytes.size },
        )

        assertEquals(
            LengthStats(total = 8296, p95 = 4, max = 5),
            actual,
            "Uniform-random insert raw-length distribution snapshot changed. actual=$actual",
        )
    }

    @Test
    fun insert_edgeBiased90_rawLengthDistribution_snapshot() {
        val actual = runInsertPatternStats(
            initialSize = 64,
            steps = 2_500,
            seed = 19,
            insertSelector = { size, _, random ->
                if (random.nextInt(100) < 90) {
                    if (random.nextBoolean()) 0 else size
                } else {
                    random.nextInt(size + 1)
                }
            },
            measure = { it.bytes.size },
        )

        assertEquals(
            LengthStats(total = 7519, p95 = 4, max = 4),
            actual,
            "Edge-biased insert raw-length distribution snapshot changed. actual=$actual",
        )
    }

    @Test
    fun move_uniformRandom_rawLengthDistribution_snapshot() {
        val actual = runMovePatternStats(
            initialSize = 256,
            steps = 4_000,
            seed = 43,
            moveSelector = { size, _, random ->
                val from = random.nextInt(size)
                var to = random.nextInt(size)
                if (size > 1 && from == to) {
                    to = (to + 1) % size
                }
                MoveSelection(from = from, to = to)
            },
            measure = { it.bytes.size },
        )

        assertEquals(
            LengthStats(total = 15461, p95 = 6, max = 8),
            actual,
            "Uniform-random move raw-length distribution snapshot changed. actual=$actual",
        )
    }

    @Test
    fun move_edgeToInner_rawLengthDistribution_snapshot() {
        val actual = runMovePatternStats(
            initialSize = 256,
            steps = 4_000,
            seed = 127,
            moveSelector = { size, step, _ ->
                if (step % 2 == 0) {
                    MoveSelection(from = 0, to = minOf(4, size - 1))
                } else {
                    MoveSelection(from = size - 1, to = (size - 5).coerceAtLeast(0))
                }
            },
            measure = { it.bytes.size },
        )

        assertEquals(
            LengthStats(total = 279544, p95 = 227, max = 252),
            actual,
            "Edge-to-inner move raw-length distribution snapshot changed. actual=$actual",
        )
    }

    @Test
    fun insert_uniformRandom_base64LengthDistribution_snapshot() {
        val actual = runInsertPatternStats(
            initialSize = 64,
            steps = 2_500,
            seed = 7,
            insertSelector = { size, _, random -> random.nextInt(size + 1) },
            measure = { it.toBase64String().length },
        )

        assertEquals(
            LengthStats(total = 13164, p95 = 8, max = 8),
            actual,
            "Uniform-random insert Base64-length distribution snapshot changed. actual=$actual",
        )
    }

    private fun runAdjacentPairPattern(
        checkpoints: List<Int>,
        measure: (FractionalIndex) -> Int,
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
                results[i] = measure(start)
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    private fun runRootAnchoredPattern(
        checkpoints: List<Int>,
        measure: (FractionalIndex) -> Int,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        val start = FractionalIndex.default()
        var end = FractionalIndexGenerator.after(start)

        for (i in 1..sorted.last()) {
            end = FractionalIndexGenerator.between(start, end).getOrThrow()
            if (i in targets) {
                results[i] = measure(end)
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    private fun runEdgeGrowthPattern(
        checkpoints: List<Int>,
        step: (FractionalIndex) -> FractionalIndex,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        var current = FractionalIndex.default()
        for (i in 1..sorted.last()) {
            current = step(current)
            if (i in targets) {
                results[i] = current.bytes.size
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    private fun runInsertPatternStats(
        initialSize: Int,
        steps: Int,
        seed: Int,
        insertSelector: (size: Int, step: Int, random: Random) -> Int,
        measure: (FractionalIndex) -> Int,
    ): LengthStats {
        val ordered = buildSequentialList(initialSize)
        val random = Random(seed)
        val measures = IntArray(steps)

        for (step in 0 until steps) {
            val insertAtRaw = insertSelector(ordered.size, step, random)
            val insertAt = insertAtRaw.coerceIn(0, ordered.size)

            val generated = when {
                insertAt == 0 -> FractionalIndexGenerator.before(ordered.first())
                insertAt == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                else -> FractionalIndexGenerator.between(ordered[insertAt - 1], ordered[insertAt]).getOrThrow()
            }

            ordered.add(insertAt, generated)
            measures[step] = measure(generated)
        }

        return measures.toLengthStats()
    }

    private fun runMovePatternStats(
        initialSize: Int,
        steps: Int,
        seed: Int,
        moveSelector: (size: Int, step: Int, random: Random) -> MoveSelection,
        measure: (FractionalIndex) -> Int,
    ): LengthStats {
        val ordered = buildSequentialList(initialSize)
        val random = Random(seed)
        val measures = IntArray(steps)

        for (step in 0 until steps) {
            val sizeBefore = ordered.size
            val selected = moveSelector(sizeBefore, step, random)
            val from = selected.from.coerceIn(0, sizeBefore - 1)

            ordered.removeAt(from)

            val to = selected.to.coerceIn(0, ordered.size)
            val generated = when {
                to == 0 -> FractionalIndexGenerator.before(ordered.first())
                to == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                else -> FractionalIndexGenerator.between(ordered[to - 1], ordered[to]).getOrThrow()
            }

            ordered.add(to, generated)
            measures[step] = measure(generated)
        }

        return measures.toLengthStats()
    }

    private fun buildSequentialList(size: Int): MutableList<FractionalIndex> {
        val ordered = mutableListOf(FractionalIndex.default())
        repeat(size - 1) {
            ordered += FractionalIndexGenerator.after(ordered.last())
        }
        return ordered
    }

    private fun IntArray.toLengthStats(): LengthStats {
        val sorted = this.sortedArray()
        val p95Index = (((sorted.size - 1) * 95) / 100).coerceIn(0, sorted.lastIndex)
        return LengthStats(
            total = this.sum(),
            p95 = sorted[p95Index],
            max = sorted.last(),
        )
    }

    private fun runSmallInwardMoveAverageLength(
        steps: Int,
        initialSize: Int,
    ): Double {
        val ordered = mutableListOf(FractionalIndex.default())
        repeat(initialSize - 1) {
            ordered += FractionalIndexGenerator.after(ordered.last())
        }

        var totalLength = 0.0
        repeat(steps) { step ->
            val moveFromHead = step % 2 == 0
            val from = if (moveFromHead) 0 else ordered.lastIndex
            ordered.removeAt(from)

            val insertAt = if (moveFromHead) {
                minOf(3, ordered.size)
            } else {
                (ordered.size - 3).coerceAtLeast(0)
            }

            val generated = when {
                insertAt == 0 -> FractionalIndexGenerator.before(ordered.first())
                insertAt == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                else -> FractionalIndexGenerator.between(ordered[insertAt - 1], ordered[insertAt]).getOrThrow()
            }

            ordered.add(insertAt, generated)
            totalLength += generated.bytes.size.toDouble()
        }

        return totalLength / steps.toDouble()
    }
}
