package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreEdgeOpsCompactSizeTest {
    private val core = FractionalIndexGeneratorCore

    /**
     * Edge bytes covering all compact-range boundaries:
     * 0x3F (just below compact), 0x40 (compact min), 0x7F, 0x80 (terminator),
     * 0xBF (compact max), 0xC0 (just above compact), 0xFE, 0xFF.
     */
    private val edgeBytes = intArrayOf(0x3F, 0x40, 0x7F, 0x80, 0xBF, 0xC0, 0xFE, 0xFF)

    @Test
    fun afterMinorCompactSizeOrNegative_matchesAllocatingPath_singleByte() {
        for (b in edgeBytes) {
            val minor = ubyteArrayOf(b.toUByte(), FractionalIndex.TERMINATOR)
            val allocating = core.afterMinor(minor)
            val expectedSize = if (FractionalIndex.isEncodableMinorForMajor(0L, allocating)) {
                allocating.size
            } else {
                -1
            }
            val actual = core.afterMinorCompactSizeOrNegative(minor)
            assertEquals(
                expectedSize,
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
                val expectedSize = if (FractionalIndex.isEncodableMinorForMajor(0L, allocating)) {
                    allocating.size
                } else {
                    -1
                }
                val actual = core.afterMinorCompactSizeOrNegative(minor)
                assertEquals(
                    expectedSize,
                    actual,
                    "afterMinorCompactSizeOrNegative mismatch for [0x${firstByte.toString(16)}, 0x${secondByte.toString(16)}]",
                )
            }
        }
    }

    @Test
    fun beforeMinorCompactSizeOrNegative_matchesAllocatingPath_singleByte() {
        for (b in edgeBytes) {
            val minor = ubyteArrayOf(b.toUByte(), FractionalIndex.TERMINATOR)
            val allocating = core.beforeMinor(minor)
            val expectedSize = if (FractionalIndex.isEncodableMinorForMajor(0L, allocating)) {
                allocating.size
            } else {
                -1
            }
            val actual = core.beforeMinorCompactSizeOrNegative(minor)
            assertEquals(
                expectedSize,
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
                val expectedSize = if (FractionalIndex.isEncodableMinorForMajor(0L, allocating)) {
                    allocating.size
                } else {
                    -1
                }
                val actual = core.beforeMinorCompactSizeOrNegative(minor)
                assertEquals(
                    expectedSize,
                    actual,
                    "beforeMinorCompactSizeOrNegative mismatch for [0x${firstByte.toString(16)}, 0x${secondByte.toString(16)}]",
                )
            }
        }
    }
}
