package dev.pon.fractionalindexing

import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorPerformanceRegressionTest {
    private data class MoveSelection(
        val from: Int,
        val to: Int,
    )

    private data class ThroughputProfile(
        val appendAfterNsPerOp: Double,
        val adjacentBetweenNsPerOp: Double,
        val randomInsertNsPerOp: Double,
        val edgeToInnerMoveNsPerOp: Double,
    )

    private data class MemoryWorkloadObservation(
        val peakUsedBytes: Long,
        val retainedUsedBytes: Long,
    )

    private data class MemoryObservation(
        val baselineUsedBytes: Long,
        val randomInsert: MemoryWorkloadObservation,
        val edgeToInnerMove: MemoryWorkloadObservation,
    )

    @Test
    fun performanceRegression_relativeProfile_staysWithinBudget() {
        val profile = getOrMeasureProfile()
        val adjacentVsRandomInsert = profile.adjacentBetweenNsPerOp / profile.randomInsertNsPerOp
        val moveVsRandomInsert = profile.edgeToInnerMoveNsPerOp / profile.randomInsertNsPerOp
        val appendVsRandomInsert = profile.appendAfterNsPerOp / profile.randomInsertNsPerOp

        assertTrue(
            adjacentVsRandomInsert <= MAX_ADJACENT_VS_RANDOM_INSERT_RATIO,
            "adjacent/random-insert ratio regressed: ratio=${"%.3f".format(adjacentVsRandomInsert)} profile=$profile",
        )
        assertTrue(
            moveVsRandomInsert <= MAX_MOVE_VS_RANDOM_INSERT_RATIO,
            "move/random-insert ratio regressed: ratio=${"%.3f".format(moveVsRandomInsert)} profile=$profile",
        )
        assertTrue(
            appendVsRandomInsert <= MAX_APPEND_VS_RANDOM_INSERT_RATIO,
            "append/random-insert ratio regressed: ratio=${"%.3f".format(appendVsRandomInsert)} profile=$profile",
        )
    }

    @Test
    fun performanceRegression_absoluteBudget_strictModeOnly() {
        if (!STRICT_MODE) return

        val profile = getOrMeasureProfile()
        assertTrue(
            profile.appendAfterNsPerOp <= MAX_APPEND_ABSOLUTE_NS_PER_OP,
            "append-after absolute budget regressed: profile=$profile",
        )
        assertTrue(
            profile.adjacentBetweenNsPerOp <= MAX_ADJACENT_ABSOLUTE_NS_PER_OP,
            "adjacent-between absolute budget regressed: profile=$profile",
        )
        assertTrue(
            profile.randomInsertNsPerOp <= MAX_RANDOM_INSERT_ABSOLUTE_NS_PER_OP,
            "random-insert absolute budget regressed: profile=$profile",
        )
        assertTrue(
            profile.edgeToInnerMoveNsPerOp <= MAX_MOVE_ABSOLUTE_NS_PER_OP,
            "edge-to-inner-move absolute budget regressed: profile=$profile",
        )
    }

    private fun getOrMeasureProfile(): ThroughputProfile {
        val cached = cachedProfile
        if (cached != null) return cached

        val measured = measureThroughputProfile()
        cachedProfile = measured
        logProfile(measured)
        if (STRICT_MODE) {
            val memory = getOrMeasureMemoryObservation()
            logMemoryObservation(memory)
        }
        return measured
    }

    private fun getOrMeasureMemoryObservation(): MemoryObservation {
        val cached = cachedMemoryObservation
        if (cached != null) return cached

        val measured = measureMemoryObservation()
        cachedMemoryObservation = measured
        return measured
    }

    private fun measureThroughputProfile(): ThroughputProfile {
        repeat(6) {
            runAppendAfter(steps = APPEND_STEPS)
            runAdjacentBetween(steps = ADJACENT_STEPS)
            runRandomInsert(steps = INSERT_STEPS, initialSize = INSERT_INITIAL_SIZE, seed = 7)
            runEdgeToInnerMove(steps = MOVE_STEPS, initialSize = MOVE_INITIAL_SIZE)
        }

        val appendNs = medianNanos(SAMPLE_COUNT) {
            runAppendAfter(steps = APPEND_STEPS)
        }
        val adjacentNs = medianNanos(SAMPLE_COUNT) {
            runAdjacentBetween(steps = ADJACENT_STEPS)
        }
        val randomInsertNs = medianNanos(SAMPLE_COUNT) {
            runRandomInsert(steps = INSERT_STEPS, initialSize = INSERT_INITIAL_SIZE, seed = 7)
        }
        val edgeToInnerMoveNs = medianNanos(SAMPLE_COUNT) {
            runEdgeToInnerMove(steps = MOVE_STEPS, initialSize = MOVE_INITIAL_SIZE)
        }

        return ThroughputProfile(
            appendAfterNsPerOp = appendNs.toDouble() / APPEND_STEPS.toDouble(),
            adjacentBetweenNsPerOp = adjacentNs.toDouble() / ADJACENT_STEPS.toDouble(),
            randomInsertNsPerOp = randomInsertNs.toDouble() / INSERT_STEPS.toDouble(),
            edgeToInnerMoveNsPerOp = edgeToInnerMoveNs.toDouble() / MOVE_STEPS.toDouble(),
        )
    }

    private fun logProfile(profile: ThroughputProfile) {
        val adjacentVsAppend = profile.adjacentBetweenNsPerOp / profile.appendAfterNsPerOp
        val randomInsertVsAppend = profile.randomInsertNsPerOp / profile.appendAfterNsPerOp
        val adjacentVsRandomInsert = profile.adjacentBetweenNsPerOp / profile.randomInsertNsPerOp
        val appendVsRandomInsert = profile.appendAfterNsPerOp / profile.randomInsertNsPerOp
        val moveVsRandomInsert = profile.edgeToInnerMoveNsPerOp / profile.randomInsertNsPerOp

        println(
            buildString {
                append("PERF_PROFILE ")
                append("append_ns_per_op=")
                append(formatDecimal(profile.appendAfterNsPerOp))
                append(" adjacent_ns_per_op=")
                append(formatDecimal(profile.adjacentBetweenNsPerOp))
                append(" random_insert_ns_per_op=")
                append(formatDecimal(profile.randomInsertNsPerOp))
                append(" edge_move_ns_per_op=")
                append(formatDecimal(profile.edgeToInnerMoveNsPerOp))
                append(" adjacent_vs_append=")
                append(formatDecimal(adjacentVsAppend))
                append(" random_insert_vs_append=")
                append(formatDecimal(randomInsertVsAppend))
                append(" adjacent_vs_random_insert=")
                append(formatDecimal(adjacentVsRandomInsert))
                append(" append_vs_random_insert=")
                append(formatDecimal(appendVsRandomInsert))
                append(" move_vs_random_insert=")
                append(formatDecimal(moveVsRandomInsert))
                append(" strict_mode=")
                append(STRICT_MODE)
            },
        )
    }

    private fun logMemoryObservation(observation: MemoryObservation) {
        println(
            buildString {
                append("PERF_MEMORY ")
                append("baseline_used_bytes=")
                append(observation.baselineUsedBytes)
                append(" random_insert_peak_bytes=")
                append(observation.randomInsert.peakUsedBytes)
                append(" random_insert_retained_bytes=")
                append(observation.randomInsert.retainedUsedBytes)
                append(" edge_move_peak_bytes=")
                append(observation.edgeToInnerMove.peakUsedBytes)
                append(" edge_move_retained_bytes=")
                append(observation.edgeToInnerMove.retainedUsedBytes)
            },
        )
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.3f", value)

    private fun measureMemoryObservation(): MemoryObservation {
        forceGc()
        val baseline = usedMemoryBytes()

        val randomInsert = observeMemoryForRandomInsert(
            steps = INSERT_STEPS,
            initialSize = INSERT_INITIAL_SIZE,
            seed = 7,
        )
        val edgeToInnerMove = observeMemoryForEdgeToInnerMove(
            steps = MOVE_STEPS,
            initialSize = MOVE_INITIAL_SIZE,
        )

        return MemoryObservation(
            baselineUsedBytes = baseline,
            randomInsert = randomInsert,
            edgeToInnerMove = edgeToInnerMove,
        )
    }

    private fun observeMemoryForRandomInsert(
        steps: Int,
        initialSize: Int,
        seed: Int,
    ): MemoryWorkloadObservation {
        forceGc()
        var peak = usedMemoryBytes()

        run {
            val ordered = buildSequentialList(initialSize)
            val random = Random(seed)
            repeat(steps) { step ->
                val insertAt = random.nextInt(ordered.size + 1)
                val generated = when {
                    insertAt == 0 -> FractionalIndexGenerator.before(ordered.first())
                    insertAt == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                    else -> FractionalIndexGenerator.between(ordered[insertAt - 1], ordered[insertAt]).getOrThrow()
                }
                ordered.add(insertAt, generated)

                if (step % MEMORY_SAMPLE_INTERVAL == 0) {
                    peak = maxOf(peak, usedMemoryBytes())
                }
            }

            blackhole = blackhole xor ordered.size.toLong()
        }

        forceGc()
        val retained = usedMemoryBytes()
        return MemoryWorkloadObservation(
            peakUsedBytes = peak,
            retainedUsedBytes = retained,
        )
    }

    private fun observeMemoryForEdgeToInnerMove(
        steps: Int,
        initialSize: Int,
    ): MemoryWorkloadObservation {
        forceGc()
        var peak = usedMemoryBytes()

        run {
            val ordered = buildSequentialList(initialSize)
            repeat(steps) { step ->
                val selection = if (step % 2 == 0) {
                    MoveSelection(from = 0, to = minOf(4, ordered.lastIndex))
                } else {
                    MoveSelection(from = ordered.lastIndex, to = (ordered.size - 5).coerceAtLeast(0))
                }

                val from = selection.from.coerceIn(0, ordered.lastIndex)
                ordered.removeAt(from)

                val to = selection.to.coerceIn(0, ordered.size)
                val generated = when {
                    to == 0 -> FractionalIndexGenerator.before(ordered.first())
                    to == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                    else -> FractionalIndexGenerator.between(ordered[to - 1], ordered[to]).getOrThrow()
                }

                ordered.add(to, generated)

                if (step % MEMORY_SAMPLE_INTERVAL == 0) {
                    peak = maxOf(peak, usedMemoryBytes())
                }
            }

            blackhole = blackhole xor ordered.size.toLong()
        }

        forceGc()
        val retained = usedMemoryBytes()
        return MemoryWorkloadObservation(
            peakUsedBytes = peak,
            retainedUsedBytes = retained,
        )
    }

    private fun usedMemoryBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun forceGc() {
        repeat(2) {
            System.gc()
            System.runFinalization()
            Thread.sleep(20L)
        }
    }

    private fun runAppendAfter(steps: Int): Int {
        var current = FractionalIndex.default()
        var checksum = 0
        repeat(steps) {
            current = FractionalIndexGenerator.after(current)
            checksum = checksum xor current.bytes.size
        }
        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun runAdjacentBetween(steps: Int): Int {
        var left = FractionalIndex.default()
        var right = FractionalIndexGenerator.after(left)
        var checksum = 0
        repeat(steps) {
            left = FractionalIndexGenerator.between(left, right).getOrThrow()
            right = FractionalIndexGenerator.between(left, right).getOrThrow()
            checksum = checksum xor left.bytes.size xor right.bytes.size
        }
        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun runRandomInsert(
        steps: Int,
        initialSize: Int,
        seed: Int,
    ): Int {
        val ordered = buildSequentialList(initialSize)
        val random = Random(seed)
        var checksum = 0

        repeat(steps) {
            val insertAt = random.nextInt(ordered.size + 1)
            val generated = when {
                insertAt == 0 -> FractionalIndexGenerator.before(ordered.first())
                insertAt == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                else -> FractionalIndexGenerator.between(ordered[insertAt - 1], ordered[insertAt]).getOrThrow()
            }
            ordered.add(insertAt, generated)
            checksum = checksum xor generated.bytes.size
        }

        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun runEdgeToInnerMove(
        steps: Int,
        initialSize: Int,
    ): Int {
        val ordered = buildSequentialList(initialSize)
        var checksum = 0

        repeat(steps) { step ->
            val selection = if (step % 2 == 0) {
                MoveSelection(from = 0, to = minOf(4, ordered.lastIndex))
            } else {
                MoveSelection(from = ordered.lastIndex, to = (ordered.size - 5).coerceAtLeast(0))
            }

            val from = selection.from.coerceIn(0, ordered.lastIndex)
            ordered.removeAt(from)

            val to = selection.to.coerceIn(0, ordered.size)
            val generated = when {
                to == 0 -> FractionalIndexGenerator.before(ordered.first())
                to == ordered.size -> FractionalIndexGenerator.after(ordered.last())
                else -> FractionalIndexGenerator.between(ordered[to - 1], ordered[to]).getOrThrow()
            }

            ordered.add(to, generated)
            checksum = checksum xor generated.bytes.size
        }

        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun buildSequentialList(size: Int): MutableList<FractionalIndex> {
        val ordered = mutableListOf(FractionalIndex.default())
        repeat(size - 1) {
            ordered += FractionalIndexGenerator.after(ordered.last())
        }
        return ordered
    }

    private fun medianNanos(samples: Int, block: () -> Any): Long {
        val values = LongArray(samples) {
            measureNanoTime {
                block()
            }
        }
        values.sort()
        return values[samples / 2]
    }

    private companion object {
        private const val SAMPLE_COUNT = 9

        private const val APPEND_STEPS = 10_000
        private const val ADJACENT_STEPS = 5_000
        private const val INSERT_STEPS = 2_500
        private const val INSERT_INITIAL_SIZE = 64
        private const val MOVE_STEPS = 3_000
        private const val MOVE_INITIAL_SIZE = 256

        private val STRICT_MODE: Boolean =
            System.getProperty("fractionalIndexing.perf.strict")?.toBooleanStrictOrNull() == true

        // Relative budgets are stable across CI/local machine differences.
        private const val MAX_ADJACENT_VS_RANDOM_INSERT_RATIO = 9.0
        private const val MAX_MOVE_VS_RANDOM_INSERT_RATIO = 2.5
        private const val MAX_APPEND_VS_RANDOM_INSERT_RATIO = 0.5

        // Absolute budgets are optional (strict mode) and intended for controlled runners.
        private const val MAX_APPEND_ABSOLUTE_NS_PER_OP = 130.0
        private const val MAX_ADJACENT_ABSOLUTE_NS_PER_OP = 4200.0
        private const val MAX_RANDOM_INSERT_ABSOLUTE_NS_PER_OP = 760.0
        private const val MAX_MOVE_ABSOLUTE_NS_PER_OP = 1000.0

        private const val MEMORY_SAMPLE_INTERVAL = 64

        @Volatile
        private var cachedProfile: ThroughputProfile? = null

        @Volatile
        private var cachedMemoryObservation: MemoryObservation? = null

        @Volatile
        private var blackhole: Long = 0L
    }
}
