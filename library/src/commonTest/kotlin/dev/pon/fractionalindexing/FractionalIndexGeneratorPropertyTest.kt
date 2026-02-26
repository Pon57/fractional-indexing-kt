package dev.pon.fractionalindexing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorPropertyTest {
    private val fractionalIndexArb = arbitrary(
        edgecases = listOf(
            FractionalIndex.default(),
            FractionalIndexGenerator.before(FractionalIndex.default()),
            FractionalIndexGenerator.after(FractionalIndex.default()),
            walkBefore(64),
            walkAfter(64),
            walkBefore(4_200),
            walkAfter(4_200),
            FractionalIndexGenerator
                .between(
                    walkBefore(2_100),
                    walkAfter(2_100),
                )
                .getOrThrow(),
        )
    ) {
        val steps = Arb.int(0..512).bind()
        var current = FractionalIndex.default()
        repeat(steps) {
            when (Arb.int(0..2).bind()) {
                0 -> current = FractionalIndexGenerator.before(current)
                1 -> current = FractionalIndexGenerator.after(current)
                else -> {
                    val other = if (Arb.int(0..1).bind() == 0) {
                        FractionalIndexGenerator.before(current)
                    } else {
                        FractionalIndexGenerator.after(current)
                    }
                    val lower = minOf(current, other)
                    val upper = maxOf(current, other)
                    current = FractionalIndexGenerator.between(lower, upper).getOrThrow()
                }
            }
        }
        current
    }

    private companion object {
        fun walkBefore(steps: Int): FractionalIndex {
            var current = FractionalIndex.default()
            repeat(steps) {
                current = FractionalIndexGenerator.before(current)
            }
            return current
        }

        fun walkAfter(steps: Int): FractionalIndex {
            var current = FractionalIndex.default()
            repeat(steps) {
                current = FractionalIndexGenerator.after(current)
            }
            return current
        }
    }

    @Test
    fun before_producesValueSmallerThanOriginal() {
        runTest {
            checkAll(fractionalIndexArb) { a ->
                val generated = FractionalIndexGenerator.before(a)
                assertTrue(
                    generated < a,
                    "Expected generated < original, generated=$generated original=$a"
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
                    "Expected generated > original, generated=$generated original=$a"
                )
            }
        }
    }

    @Test
    fun between_producesValueStrictlyInsideBounds() {
        runTest {
            checkAll(fractionalIndexArb, fractionalIndexArb) { a, b ->
                if (a == b) return@checkAll

                val lower = minOf(a, b)
                val upper = maxOf(a, b)
                val generated = FractionalIndexGenerator.between(lower, upper).getOrThrow()

                assertTrue(
                    generated > lower,
                    "Expected generated > lower, generated=$generated lower=$lower"
                )
                assertTrue(
                    generated < upper,
                    "Expected generated < upper, generated=$generated upper=$upper"
                )
            }
        }
    }

    @Test
    fun allOperations_produceIndexesEndingWithTerminator() {
        runTest {
            checkAll(fractionalIndexArb) { a ->
                val before = FractionalIndexGenerator.before(a)
                val after = FractionalIndexGenerator.after(a)
                val between = FractionalIndexGenerator.between(before, after).getOrThrow()

                assertEquals(FractionalIndex.TERMINATOR, before.bytes.last())
                assertEquals(FractionalIndex.TERMINATOR, after.bytes.last())
                assertEquals(FractionalIndex.TERMINATOR, between.bytes.last())
            }
        }
    }
}
