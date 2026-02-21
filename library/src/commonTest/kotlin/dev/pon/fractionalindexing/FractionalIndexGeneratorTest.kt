package dev.pon.fractionalindexing

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.uByte
import io.kotest.property.arbitrary.uByteArray
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class FractionalIndexGeneratorTest {
    private fun index(hex: String): FractionalIndex =
        FractionalIndex.fromBytes(parseHex(hex)).getOrThrow()

    private fun encodedFromHex(hex: String): String =
        index(hex).toBase64String()

    private fun parseHex(hex: String): UByteArray {
        require(hex.isNotEmpty() && hex.length % 2 == 0) { "Invalid hex string: $hex" }
        require(hex.all { it.isHexDigit() }) { "Invalid hex string: $hex" }
        return hex.chunked(2).map { it.toUByte(16) }.toUByteArray()
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }

    @Test
    fun before_matchesKnownVectors() {
        val cases = listOf(
            Triple("80", "7f80", "7e80"),
            Triple("64640380", "6380", "6280"),
            Triple("000080", "00007f80", "00007e80"),
            Triple("0080", "007f80", "007e80"),
        )

        for ((input, expectedFirst, expectedSecond) in cases) {
            val first = FractionalIndexGenerator.before(index(input))
            val second = FractionalIndexGenerator.before(first)

            assertEquals(encodedFromHex(expectedFirst), first.toBase64String(), "before($input)")
            assertEquals(encodedFromHex(expectedSecond), second.toBase64String(), "before(before($input))")
        }
    }

    @Test
    fun after_matchesKnownVectors() {
        val cases = listOf(
            Triple("80", "8180", "8280"),
            Triple("f0f00380", "f180", "f280"),
            Triple("ffff80", "ffff8180", "ffff8280"),
            Triple("ff80", "ff8180", "ff8280"),
        )

        for ((input, expectedFirst, expectedSecond) in cases) {
            val first = FractionalIndexGenerator.after(index(input))
            val second = FractionalIndexGenerator.after(first)

            assertEquals(encodedFromHex(expectedFirst), first.toBase64String(), "after($input)")
            assertEquals(encodedFromHex(expectedSecond), second.toBase64String(), "after(after($input))")
        }
    }

    @Test
    fun between_minimalMatchesKnownVectors() {
        val cases = listOf(
            Triple("6480", "7780", "6d80"),
            Triple("646480", "646880", "646680"),
            Triple("646480", "646780", "646580"),
            Triple("646480", "646680", "646580"),
            Triple("6c80", "6d80", "6c8180"),
            Triple("7f8080", "8080", "7f8180"),
            Triple("7f8180", "80", "7f8280"),
            Triple("7f80", "80", "7f8180"),
            Triple("6480", "649080", "64907f80"),
            Triple("647a80", "6480", "647a8180"),
            Triple("647a80", "648080", "647d80"),
            Triple("80", "80c080", "8080"),
        )

        for ((leftHex, rightHex, expectedHex) in cases) {
            val left = index(leftHex)
            val right = index(rightHex)
            val mid = FractionalIndexGenerator
                .between(left, right, FractionalIndexGenerator.BetweenStrategy.MINIMAL)
                .getOrThrow()

            assertEquals(encodedFromHex(expectedHex), mid.toBase64String(), "between($leftHex, $rightHex)")
        }
    }

    @Test
    fun between_defaultUsesSpreadStrategy() {
        val left = index("6c80")
        val right = index("6d80")

        val defaultGenerated = FractionalIndexGenerator.between(left, right).getOrThrow()
        val spreadGenerated = FractionalIndexGenerator
            .between(left, right, FractionalIndexGenerator.BetweenStrategy.SPREAD)
            .getOrThrow()

        assertEquals(spreadGenerated, defaultGenerated, "between(left, right) should use SPREAD by default")
    }

    @Test
    fun spreadStrategy_reducesAdjacentWorstCaseGrowth() {
        val checkpoints = listOf(300, 3_000)
        val minimal = runAdjacentPairPattern(checkpoints, FractionalIndexGenerator.BetweenStrategy.MINIMAL)
        val spread = runAdjacentPairPattern(checkpoints, FractionalIndexGenerator.BetweenStrategy.SPREAD)

        for (checkpoint in checkpoints) {
            assertTrue(
                spread.getValue(checkpoint) <= minimal.getValue(checkpoint),
                "Expected spread <= minimal at $checkpoint, spread=${spread.getValue(checkpoint)} minimal=${minimal.getValue(checkpoint)}"
            )
        }
    }

    @Test
    fun spreadStrategy_doesNotWorsenRootAnchoredPattern() {
        val checkpoints = listOf(300, 3_000)
        val minimal = runRootAnchoredPattern(checkpoints, FractionalIndexGenerator.BetweenStrategy.MINIMAL)
        val spread = runRootAnchoredPattern(checkpoints, FractionalIndexGenerator.BetweenStrategy.SPREAD)

        for (checkpoint in checkpoints) {
            assertTrue(
                spread.getValue(checkpoint) <= minimal.getValue(checkpoint),
                "Expected spread <= minimal at $checkpoint, spread=${spread.getValue(checkpoint)} minimal=${minimal.getValue(checkpoint)}"
            )
        }
    }

    @Test
    fun between_returnsIndexStrictlyInsideBounds() {
        val left = index("7f80")
        val right = index("80")

        val mid = FractionalIndexGenerator.between(left, right).getOrThrow()

        assertTrue(left < mid, "Expected mid to be greater than left")
        assertTrue(mid < right, "Expected mid to be less than right")
    }

    @Test
    fun between_acceptsUnorderedBounds() {
        val lower = index("7f80")
        val upper = index("80")

        val forward = FractionalIndexGenerator.between(lower, upper).getOrThrow()
        val reversed = FractionalIndexGenerator.between(upper, lower).getOrThrow()

        assertTrue(forward > lower, "Expected forward result > lower")
        assertTrue(forward < upper, "Expected forward result < upper")
        assertTrue(reversed > lower, "Expected reversed result > lower")
        assertTrue(reversed < upper, "Expected reversed result < upper")
        assertEquals(forward, reversed, "Expected between to be order-independent for the same bounds")
    }

    @Test
    fun between_rejectsEqualBounds() {
        val same = FractionalIndex.default()

        val exception = assertFailsWith<IllegalArgumentException> {
            FractionalIndexGenerator.between(same, same).getOrThrow()
        }

        assertEquals("bounds must be distinct", exception.message)
    }

    @Test
    fun generatorOperations_doNotMutateInputIndexes() {
        val left = index("7f80")
        val right = index("8180")
        val leftSnapshot = left.toHexString()
        val rightSnapshot = right.toHexString()

        FractionalIndexGenerator.before(right)
        FractionalIndexGenerator.after(left)
        FractionalIndexGenerator.between(left, right).getOrThrow()

        assertEquals(leftSnapshot, left.toHexString(), "left input should remain unchanged")
        assertEquals(rightSnapshot, right.toHexString(), "right input should remain unchanged")
    }

    // region Property-based tests
    private val fractionalIndexArb = arbitrary(
        edgecases = listOf(
            FractionalIndex(ubyteArrayOf(FractionalIndex.TERMINATOR)),
            FractionalIndex(ubyteArrayOf(0x00u, FractionalIndex.TERMINATOR)),
            FractionalIndex(ubyteArrayOf(0xFFu, FractionalIndex.TERMINATOR)),
            FractionalIndex(ubyteArrayOf(0xFFu, 0xFFu, 0xFFu, FractionalIndex.TERMINATOR))
        )
    ) {
        val randomBytes = Arb.uByteArray(Arb.int(0..10), Arb.uByte()).bind()
        val validPayload = randomBytes.filter { it != FractionalIndex.TERMINATOR }.toUByteArray()
        val finalBytes = UByteArray(validPayload.size + 1)
        validPayload.copyInto(finalBytes)
        finalBytes[validPayload.size] = FractionalIndex.TERMINATOR
        FractionalIndex(finalBytes)
    }

    private fun runAdjacentPairPattern(
        checkpoints: List<Int>,
        strategy: FractionalIndexGenerator.BetweenStrategy,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        var start = FractionalIndex.default()
        var end = FractionalIndexGenerator.after(start)

        for (i in 1..sorted.last()) {
            start = FractionalIndexGenerator.between(start, end, strategy).getOrThrow()
            end = FractionalIndexGenerator.between(start, end, strategy).getOrThrow()

            if (i in targets) {
                results[i] = start.bytes.size
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    private fun runRootAnchoredPattern(
        checkpoints: List<Int>,
        strategy: FractionalIndexGenerator.BetweenStrategy,
    ): LinkedHashMap<Int, Int> {
        val sorted = checkpoints.sorted()
        val targets = sorted.toSet()
        val results = linkedMapOf<Int, Int>()

        val start = FractionalIndex.default()
        var end = FractionalIndexGenerator.after(start)

        for (i in 1..sorted.last()) {
            end = FractionalIndexGenerator.between(start, end, strategy).getOrThrow()
            if (i in targets) {
                results[i] = end.bytes.size
            }
        }

        return LinkedHashMap(checkpoints.associateWith { results.getValue(it) })
    }

    @Test
    fun rule1_beforeShouldBeSmallerThanOriginal() {
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
    fun rule2_afterShouldBeGreaterThanOriginal() {
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
    fun rule3_betweenShouldBeStrictlyInsideBounds() {
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
    fun rule4_allGeneratedIndexesShouldEndWithTerminator() {
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
    // endregion
}
