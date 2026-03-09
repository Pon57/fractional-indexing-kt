package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoreEdgeOpsCompactSizeTest {
    private val core = FractionalIndexGeneratorCore

    /**
     * Edge bytes covering all compact-range boundaries:
     * 0x3F (just below compact), 0x40 (compact min), 0x7F, 0x80 (terminator),
     * 0xBF (compact max), 0xC0 (just above compact), 0xFE, 0xFF.
     */
    private val edgeBytes = intArrayOf(0x3F, 0x40, 0x7F, 0x80, 0xBF, 0xC0, 0xFE, 0xFF)

    // --- afterMinorCompactSizeOrNegative equivalence ---

    @Test
    fun afterMinorCompactSizeOrNegative_matchesAllocatingPath_singleByte() {
        for (b in edgeBytes) {
            val minor = ubyteArrayOf(b.toUByte(), FractionalIndex.TERMINATOR)
            val allocating = core.afterMinor(minor)
            val expected = compactSizeOrNegative(allocating)
            val actual = core.afterMinorCompactSizeOrNegative(minor)
            assertEquals(
                expected,
                actual,
                "afterMinorCompactSizeOrNegative mismatch for first byte 0x${b.toString(16)} at i=0",
            )
        }
    }

    @Test
    fun afterMinorCompactSizeOrNegative_matchesAllocatingPath_secondByte() {
        for (firstByte in edgeBytes) {
            for (secondByte in edgeBytes) {
                val minor = ubyteArrayOf(
                    firstByte.toUByte(),
                    secondByte.toUByte(),
                    FractionalIndex.TERMINATOR,
                )
                val allocating = core.afterMinor(minor)
                val expected = compactSizeOrNegative(allocating)
                val actual = core.afterMinorCompactSizeOrNegative(minor)
                assertEquals(
                    expected,
                    actual,
                    "afterMinorCompactSizeOrNegative mismatch for [0x${firstByte.toString(16)}, 0x${secondByte.toString(16)}]",
                )
            }
        }
    }

    @Test
    fun afterMinorCompactSizeOrNegative_matchesAllocatingPath_deepScanPosition() {
        // 0xFF causes afterMinor scan to continue (not < TERMINATOR, not < 0xFF).
        // These test scan positions i=2 and i=3.
        val deepMinors = listOf(
            ubyteArrayOf(0xFFu, 0xFFu, 0x80u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0xFFu, 0xFFu, 0xFEu, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0xFFu, 0xFFu, 0x40u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0xFFu, 0xFFu, 0xFFu, 0x80u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0xFFu, 0xFFu, 0xFFu, 0x7Fu, FractionalIndex.TERMINATOR),
            // Compact first byte with deep scan
            ubyteArrayOf(0x80u, 0xFFu, 0xFFu, 0x40u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0xBFu, 0xFFu, 0x7Fu, FractionalIndex.TERMINATOR),
            // Non-compact first byte with deep scan
            ubyteArrayOf(0xC0u, 0xFFu, 0xFFu, 0x40u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x3Fu, 0xFFu, 0x7Fu, FractionalIndex.TERMINATOR),
        )
        for (minor in deepMinors) {
            val allocating = core.afterMinor(minor)
            val expected = compactSizeOrNegative(allocating)
            val actual = core.afterMinorCompactSizeOrNegative(minor)
            assertEquals(
                expected,
                actual,
                "afterMinorCompactSizeOrNegative mismatch for deep minor ${minor.contentToString()}",
            )
        }
    }

    // --- beforeMinorCompactSizeOrNegative equivalence ---

    @Test
    fun beforeMinorCompactSizeOrNegative_matchesAllocatingPath_singleByte() {
        for (b in edgeBytes) {
            val minor = ubyteArrayOf(b.toUByte(), FractionalIndex.TERMINATOR)
            val allocating = core.beforeMinor(minor)
            val expected = compactSizeOrNegative(allocating)
            val actual = core.beforeMinorCompactSizeOrNegative(minor)
            assertEquals(
                expected,
                actual,
                "beforeMinorCompactSizeOrNegative mismatch for first byte 0x${b.toString(16)} at i=0",
            )
        }
    }

    @Test
    fun beforeMinorCompactSizeOrNegative_matchesAllocatingPath_secondByte() {
        for (firstByte in edgeBytes) {
            for (secondByte in edgeBytes) {
                val minor = ubyteArrayOf(
                    firstByte.toUByte(),
                    secondByte.toUByte(),
                    FractionalIndex.TERMINATOR,
                )
                val allocating = core.beforeMinor(minor)
                val expected = compactSizeOrNegative(allocating)
                val actual = core.beforeMinorCompactSizeOrNegative(minor)
                assertEquals(
                    expected,
                    actual,
                    "beforeMinorCompactSizeOrNegative mismatch for [0x${firstByte.toString(16)}, 0x${secondByte.toString(16)}]",
                )
            }
        }
    }

    @Test
    fun beforeMinorCompactSizeOrNegative_matchesAllocatingPath_deepScanPosition() {
        // 0x00 causes beforeMinor scan to continue (not > TERMINATOR, not > 0).
        // These test scan positions i=2 and i=3.
        val deepMinors = listOf(
            ubyteArrayOf(0x00u, 0x00u, 0x80u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x00u, 0x00u, 0x01u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x00u, 0x00u, 0xBFu, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x00u, 0x00u, 0x00u, 0x80u, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x00u, 0x00u, 0x00u, 0x81u, FractionalIndex.TERMINATOR),
            // Compact first byte with deep scan
            ubyteArrayOf(0x80u, 0x00u, 0x00u, 0xBFu, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x40u, 0x00u, 0x81u, FractionalIndex.TERMINATOR),
            // Non-compact first byte with deep scan
            ubyteArrayOf(0xC0u, 0x00u, 0x00u, 0xBFu, FractionalIndex.TERMINATOR),
            ubyteArrayOf(0x3Fu, 0x00u, 0x81u, FractionalIndex.TERMINATOR),
        )
        for (minor in deepMinors) {
            val allocating = core.beforeMinor(minor)
            val expected = compactSizeOrNegative(allocating)
            val actual = core.beforeMinorCompactSizeOrNegative(minor)
            assertEquals(
                expected,
                actual,
                "beforeMinorCompactSizeOrNegative mismatch for deep minor ${minor.contentToString()}",
            )
        }
    }

    // --- Malformed input equivalence ---

    @Test
    fun afterMinorCompactSizeOrNegative_throwsOnMalformedInput() {
        // All 0xFF bytes — no valid increment point, same as afterMinor
        val malformed = ubyteArrayOf(0xFFu, 0xFFu, 0xFFu)
        assertFailsWith<IllegalStateException> {
            core.afterMinor(malformed)
        }
        assertFailsWith<IllegalStateException> {
            core.afterMinorCompactSizeOrNegative(malformed)
        }
    }

    @Test
    fun beforeMinorCompactSizeOrNegative_throwsOnMalformedInput() {
        // All 0x00 bytes — no valid decrement point, same as beforeMinor
        val malformed = ubyteArrayOf(0x00u, 0x00u, 0x00u)
        assertFailsWith<IllegalStateException> {
            core.beforeMinor(malformed)
        }
        assertFailsWith<IllegalStateException> {
            core.beforeMinorCompactSizeOrNegative(malformed)
        }
    }

    private fun compactSizeOrNegative(minor: UByteArray): Int =
        if (FractionalIndex.isEncodableMinorForMajor(0L, minor)) minor.size else -1
}
