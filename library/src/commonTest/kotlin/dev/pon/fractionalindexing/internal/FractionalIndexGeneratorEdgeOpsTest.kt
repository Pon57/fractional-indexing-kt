package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorEdgeOpsTest {
    private val fractionalIndexArb = FractionalIndexGeneratorTestFixtures.fractionalIndexArb

    @Test
    fun before_producesValueSmallerThanOriginal() {
        runTest {
            checkAll(fractionalIndexArb) { a ->
                val generated = FractionalIndexGenerator.before(a)
                assertTrue(
                    generated < a,
                    "Expected generated < original, generated=$generated original=$a",
                )
            }
        }
    }

    @Test
    fun after_producesValueGreaterThanOriginal() {
        runTest {
            checkAll(fractionalIndexArb) { a ->
                val generated = FractionalIndexGenerator.after(a)
                assertTrue(
                    generated > a,
                    "Expected generated > original, generated=$generated original=$a",
                )
            }
        }
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
            "Expected size <= 2 at 100 inserts, actual=${lengthsAtCheckpoints.getValue(100)}",
        )
        // Medium major range (Plateau)
        assertTrue(
            lengthsAtCheckpoints.getValue(1_000) <= 3,
            "Expected size <= 3 at 1,000 inserts, actual=${lengthsAtCheckpoints.getValue(1_000)}",
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_000) <= 3,
            "Expected size <= 3 at 4,000 inserts, actual=${lengthsAtCheckpoints.getValue(4_000)}",
        )
        // Spilled to extended major
        assertTrue(
            lengthsAtCheckpoints.getValue(4_500) > 3,
            "Expected size > 3 at 4,500 inserts, actual=${lengthsAtCheckpoints.getValue(4_500)}",
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
            "Expected size <= 2 at 100 inserts, actual=${lengthsAtCheckpoints.getValue(100)}",
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(1_000) <= 3,
            "Expected size <= 3 at 1,000 inserts, actual=${lengthsAtCheckpoints.getValue(1_000)}",
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_000) <= 3,
            "Expected size <= 3 at 4,000 inserts, actual=${lengthsAtCheckpoints.getValue(4_000)}",
        )
        assertTrue(
            lengthsAtCheckpoints.getValue(4_500) > 3,
            "Expected size > 3 at 4,500 inserts, actual=${lengthsAtCheckpoints.getValue(4_500)}",
        )
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
}
