package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorOrderingScenariosTest {
    private val fractionalIndexArb = FractionalIndexGeneratorTestFixtures.fractionalIndexArb

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
    fun between_producesValueStrictlyInsideBounds() {
        runTest {
            checkAll(fractionalIndexArb, fractionalIndexArb) { a, b ->
                if (a == b) return@checkAll

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

    private fun assertStrictlyAscending(items: List<FractionalIndex>, step: Int) {
        for (i in 1 until items.size) {
            assertTrue(
                items[i - 1] < items[i],
                "Expected strict ascending order at step=$step index=$i left=${items[i - 1].toHexString()} right=${items[i].toHexString()}",
            )
        }
    }

    private fun assertLocalOrderingAroundInsertion(items: List<FractionalIndex>, insertedIndex: Int, step: Int) {
        if (insertedIndex > 0) {
            val left = items[insertedIndex - 1]
            val inserted = items[insertedIndex]
            assertTrue(
                left < inserted,
                "Expected left < inserted at step=$step index=$insertedIndex left=${left.toHexString()} inserted=${inserted.toHexString()}",
            )
        }
        if (insertedIndex < items.lastIndex) {
            val inserted = items[insertedIndex]
            val right = items[insertedIndex + 1]
            assertTrue(
                inserted < right,
                "Expected inserted < right at step=$step index=$insertedIndex inserted=${inserted.toHexString()} right=${right.toHexString()}",
            )
        }
    }
}
