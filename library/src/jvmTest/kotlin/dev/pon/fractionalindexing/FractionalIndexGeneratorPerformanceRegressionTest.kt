package dev.pon.fractionalindexing

import java.lang.ref.Reference
import java.util.Locale
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class FractionalIndexGeneratorPerformanceRegressionTest {
    private data class MoveSelection(
        val from: Int,
        val to: Int,
    )

    private data class GenerationRequest(
        val left: FractionalIndex?,
        val right: FractionalIndex?,
    )

    private data class ThroughputProfile(
        val appendAfterNsPerOp: Double,
        val adjacentBetweenNsPerOp: Double,
        val randomInsertNsPerOp: Double,
        val edgeToInnerMoveNsPerOp: Double,
        val boundedRebalanceNsPerKey: Double,
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
    fun performanceRegression_absoluteBudget_staysWithinBudget() {
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
        assertTrue(
            profile.boundedRebalanceNsPerKey <= MAX_BOUNDED_REBALANCE_ABSOLUTE_NS_PER_KEY,
            "bounded-rebalance absolute budget regressed: profile=$profile",
        )
    }

    private fun getOrMeasureProfile(): ThroughputProfile {
        val cached = cachedProfile
        if (cached != null) return cached

        val measured = measureThroughputProfile()
        cachedProfile = measured
        logProfile(measured)
        val memory = getOrMeasureMemoryObservation()
        logMemoryObservation(memory)
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
        // Build evolving-list scenarios outside the timed blocks so list shifts and setup do not
        // get attributed to FractionalIndex generation.
        val randomInsertRequests = buildRandomInsertRequests(
            steps = INSERT_STEPS,
            initialSize = INSERT_INITIAL_SIZE,
            seed = 7,
        )
        val edgeToInnerMoveRequests = buildEdgeToInnerMoveRequests(
            steps = MOVE_STEPS,
            initialSize = MOVE_INITIAL_SIZE,
        )

        repeat(6) {
            runAppendAfter(steps = APPEND_STEPS)
            runAdjacentBetween(steps = ADJACENT_STEPS)
            runGenerationRequests(randomInsertRequests)
            runGenerationRequests(edgeToInnerMoveRequests)
            runBoundedRebalance(count = REBALANCE_COUNT)
        }

        val appendNs = medianNanos(SAMPLE_COUNT) {
            runAppendAfter(steps = APPEND_STEPS)
        }
        val adjacentNs = medianNanos(SAMPLE_COUNT) {
            runAdjacentBetween(steps = ADJACENT_STEPS)
        }
        val randomInsertNs = medianNanos(SAMPLE_COUNT) {
            runGenerationRequests(randomInsertRequests)
        }
        val edgeToInnerMoveNs = medianNanos(SAMPLE_COUNT) {
            runGenerationRequests(edgeToInnerMoveRequests)
        }
        val rebalanceNs = medianNanos(SAMPLE_COUNT) {
            runBoundedRebalance(count = REBALANCE_COUNT)
        }

        return ThroughputProfile(
            appendAfterNsPerOp = appendNs.toDouble() / APPEND_STEPS.toDouble(),
            adjacentBetweenNsPerOp = adjacentNs.toDouble() / (ADJACENT_STEPS.toDouble() * 2.0),
            randomInsertNsPerOp = randomInsertNs.toDouble() / INSERT_STEPS.toDouble(),
            edgeToInnerMoveNsPerOp = edgeToInnerMoveNs.toDouble() / MOVE_STEPS.toDouble(),
            boundedRebalanceNsPerKey = rebalanceNs.toDouble() / REBALANCE_COUNT.toDouble(),
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
                append(" bounded_rebalance_ns_per_key=")
                append(formatDecimal(profile.boundedRebalanceNsPerKey))
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

        forceGc()
        val retained = usedMemoryBytes()
        blackhole = blackhole xor ordered.size.toLong()
        Reference.reachabilityFence(ordered)
        return MemoryWorkloadObservation(
            peakUsedBytes = maxOf(peak, retained),
            retainedUsedBytes = retained,
        )
    }

    private fun observeMemoryForEdgeToInnerMove(
        steps: Int,
        initialSize: Int,
    ): MemoryWorkloadObservation {
        forceGc()
        var peak = usedMemoryBytes()

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

        forceGc()
        val retained = usedMemoryBytes()
        blackhole = blackhole xor ordered.size.toLong()
        Reference.reachabilityFence(ordered)
        return MemoryWorkloadObservation(
            peakUsedBytes = maxOf(peak, retained),
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
            checksum = checksum xor current.encodedLength
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
            checksum = checksum xor left.encodedLength xor right.encodedLength
        }
        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun buildRandomInsertRequests(
        steps: Int,
        initialSize: Int,
        seed: Int,
    ): List<GenerationRequest> {
        val ordered = buildSequentialList(initialSize)
        val random = Random(seed)
        val requests = ArrayList<GenerationRequest>(steps)

        repeat(steps) {
            val insertAt = random.nextInt(ordered.size + 1)
            val request = generationRequestAt(ordered, insertAt)
            requests += request
            ordered.add(insertAt, request.generate())
        }

        return requests
    }

    private fun buildEdgeToInnerMoveRequests(
        steps: Int,
        initialSize: Int,
    ): List<GenerationRequest> {
        val ordered = buildSequentialList(initialSize)
        val requests = ArrayList<GenerationRequest>(steps)

        repeat(steps) { step ->
            val selection = if (step % 2 == 0) {
                MoveSelection(from = 0, to = minOf(4, ordered.lastIndex))
            } else {
                MoveSelection(from = ordered.lastIndex, to = (ordered.size - 5).coerceAtLeast(0))
            }

            val from = selection.from.coerceIn(0, ordered.lastIndex)
            ordered.removeAt(from)

            val to = selection.to.coerceIn(0, ordered.size)
            val request = generationRequestAt(ordered, to)
            requests += request
            ordered.add(to, request.generate())
        }

        return requests
    }

    private fun generationRequestAt(
        ordered: List<FractionalIndex>,
        index: Int,
    ): GenerationRequest = when (index) {
        0 -> GenerationRequest(left = null, right = ordered.first())
        ordered.size -> GenerationRequest(left = ordered.last(), right = null)
        else -> GenerationRequest(left = ordered[index - 1], right = ordered[index])
    }

    private fun GenerationRequest.generate(): FractionalIndex = when {
        left == null -> FractionalIndexGenerator.before(requireNotNull(right))
        right == null -> FractionalIndexGenerator.after(requireNotNull(left))
        else -> FractionalIndexGenerator.between(left, right).getOrThrow()
    }

    private fun runGenerationRequests(requests: List<GenerationRequest>): Int {
        var checksum = 0
        for (request in requests) {
            checksum = checksum xor request.generate().encodedLength
        }

        blackhole = blackhole xor checksum.toLong()
        return checksum
    }

    private fun runBoundedRebalance(count: Int): Int {
        val default = FractionalIndex.default()
        val generated = FractionalIndexGenerator.rebalanceOrThrow(
            count = count,
            lowerEndpoint = FractionalIndexGenerator.before(default),
            upperEndpoint = FractionalIndexGenerator.after(default),
        )
        val checksum = generated.first().encodedLength xor generated.last().encodedLength xor generated.size
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
        private const val REBALANCE_COUNT = 10_000

        // Absolute budgets are enforced on every JVM test run so regressions surface in PRs.
        private const val MAX_APPEND_ABSOLUTE_NS_PER_OP = 120.0
        private const val MAX_ADJACENT_ABSOLUTE_NS_PER_OP = 3400.0
        private const val MAX_RANDOM_INSERT_ABSOLUTE_NS_PER_OP = 550.0
        private const val MAX_MOVE_ABSOLUTE_NS_PER_OP = 800.0
        private const val MAX_BOUNDED_REBALANCE_ABSOLUTE_NS_PER_KEY = 8_000.0

        private const val MEMORY_SAMPLE_INTERVAL = 64

        @Volatile
        private var cachedProfile: ThroughputProfile? = null

        @Volatile
        private var cachedMemoryObservation: MemoryObservation? = null

        @Volatile
        private var blackhole: Long = 0L
    }
}
