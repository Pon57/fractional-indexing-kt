package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorBetweenSameMajorTest {
    private val fractionalIndexArb = FractionalIndexGeneratorTestFixtures.fractionalIndexArb

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
            "Expected between() with identical bounds to fail",
        )
        assertEquals(
            "bounds must be distinct",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun betweenOrThrow_withIdenticalBounds_throws() {
        val index = FractionalIndex.default()

        assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.betweenOrThrow(index, index)
        }
    }

    @Test
    fun between_withSameMajorBounds_producesValueStrictlyInsideBounds() {
        runTest {
            checkAll(fractionalIndexArb, fractionalIndexArb) { a, b ->
                if (a == b || a.major != b.major) return@checkAll

                val lower = minOf(a, b)
                val upper = maxOf(a, b)
                val generated = FractionalIndexGenerator.between(lower, upper).getOrThrow()

                assertTrue(
                    generated > lower,
                    "Expected generated > lower, generated=$generated lower=$lower",
                )
                assertTrue(
                    generated < upper,
                    "Expected generated < upper, generated=$generated upper=$upper",
                )
            }
        }
    }
}
