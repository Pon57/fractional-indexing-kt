package dev.pon.fractionalindexing.internal

import dev.pon.fractionalindexing.FractionalIndex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoreRebalanceMinorBuildersTest {

    // ── buildPivotMinorWithAfterRank ────────────────────────────────────

    @Test
    fun afterRank_rank1_producesSingleSuffixByte() {
        // rank=1: suffixRepeatCount=0, terminalByte=0x81
        // result: [pivotByte, 0x81, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 1,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x81u, 0x80u), minor)
    }

    @Test
    fun afterRank_rank127_fillsFirstSuffixByteToMax() {
        // rank=127: suffixRepeatCount=0, terminalByte=0x80+127=0xFF
        // result: [pivotByte, 0xFF, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 127,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0xFFu, 0x80u), minor)
    }

    @Test
    fun afterRank_rank128_overflowsToSecondRepeatByte() {
        // rank=128: suffixRepeatCount=1, terminalByte=0x81
        // result: [pivotByte, 0xFF, 0x81, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 128,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0xFFu, 0x81u, 0x80u), minor)
    }

    @Test
    fun afterRank_rank254_fillsSecondRepeatByteToMax() {
        // rank=254: suffixRepeatCount=1, terminalByte=0x80+127=0xFF
        // result: [pivotByte, 0xFF, 0xFF, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 254,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0xFFu, 0xFFu, 0x80u), minor)
    }

    @Test
    fun afterRank_rank255_overflowsToThirdRepeatByte() {
        // rank=255: suffixRepeatCount=2, terminalByte=0x81
        // result: [pivotByte, 0xFF, 0xFF, 0x81, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 255,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0xFFu, 0xFFu, 0x81u, 0x80u), minor)
    }

    @Test
    fun afterRank_largeRank_producesCorrectRepeatAndTerminal() {
        // rank=508: suffixRepeatCount=(507)/127=3, remainder=507%127=126, terminalByte=0x80+127=0xFF
        // result: [pivotByte, 0xFF, 0xFF, 0xFF, 0xFF, 0x80]
        val minor = buildPivotMinorWithAfterRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 508,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0xFFu, 0xFFu, 0xFFu, 0xFFu, 0x80u), minor)
    }

    @Test
    fun afterRank_withPrefix_prefixIsCopied() {
        val prefix = ubyteArrayOf(0x50u, 0x60u)
        val minor = buildPivotMinorWithAfterRank(
            prefix = prefix,
            prefixLength = 2,
            pivotByte = 0xA0u,
            rank = 1,
        )
        assertContentEquals(ubyteArrayOf(0x50u, 0x60u, 0xA0u, 0x81u, 0x80u), minor)
    }

    @Test
    fun afterRank_withLargeRankAndPrefix_producesCorrectLayout() {
        val prefix = ubyteArrayOf(0x50u)
        // rank=128: suffixRepeatCount=1, terminalByte=0x81
        val minor = buildPivotMinorWithAfterRank(
            prefix = prefix,
            prefixLength = 1,
            pivotByte = 0xA0u,
            rank = 128,
        )
        assertContentEquals(ubyteArrayOf(0x50u, 0xA0u, 0xFFu, 0x81u, 0x80u), minor)
    }

    @Test
    fun afterRank_consecutiveRanks_areStrictlyIncreasing() {
        val ranks = listOf(1, 2, 126, 127, 128, 129, 254, 255, 256, 500)
        val minors = ranks.map { rank ->
            buildPivotMinorWithAfterRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
        }
        for (i in 0 until minors.lastIndex) {
            assertTrue(
                compareUByteArrays(minors[i], minors[i + 1]) < 0,
                "afterRank(${ranks[i]}) must be < afterRank(${ranks[i + 1]})",
            )
        }
    }

    @Test
    fun afterRank_zeroRank_throws() {
        assertFailsWith<IllegalArgumentException> {
            buildPivotMinorWithAfterRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = 0,
            )
        }
    }

    // ── buildPivotMinorWithBeforeRank ───────────────────────────────────

    @Test
    fun beforeRank_rank1_producesSingleSuffixByte() {
        // rank=1: suffixRepeatCount=0, terminalByte=0x80-1=0x7F
        // result: [pivotByte, 0x7F, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 1,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x7Fu, 0x80u), minor)
    }

    @Test
    fun beforeRank_rank128_fillsFirstSuffixByteToMin() {
        // rank=128: suffixRepeatCount=0, terminalByte=0x80-128=0x00
        // result: [pivotByte, 0x00, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 128,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x00u, 0x80u), minor)
    }

    @Test
    fun beforeRank_rank129_overflowsToSecondRepeatByte() {
        // rank=129: suffixRepeatCount=1, terminalByte=0x7F
        // result: [pivotByte, 0x00, 0x7F, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 129,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x00u, 0x7Fu, 0x80u), minor)
    }

    @Test
    fun beforeRank_rank256_fillsSecondRepeatByteToMin() {
        // rank=256: suffixRepeatCount=1, terminalByte=0x00
        // result: [pivotByte, 0x00, 0x00, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 256,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x00u, 0x00u, 0x80u), minor)
    }

    @Test
    fun beforeRank_rank257_overflowsToThirdRepeatByte() {
        // rank=257: suffixRepeatCount=2, terminalByte=0x7F
        // result: [pivotByte, 0x00, 0x00, 0x7F, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 257,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x00u, 0x00u, 0x7Fu, 0x80u), minor)
    }

    @Test
    fun beforeRank_largeRank_producesCorrectRepeatAndTerminal() {
        // rank=512: suffixRepeatCount=(511)/128=3, remainder=511%128=127, terminalByte=0x80-128=0x00
        // result: [pivotByte, 0x00, 0x00, 0x00, 0x00, 0x80]
        val minor = buildPivotMinorWithBeforeRank(
            prefix = ubyteArrayOf(),
            prefixLength = 0,
            pivotByte = 0xA0u,
            rank = 512,
        )
        assertContentEquals(ubyteArrayOf(0xA0u, 0x00u, 0x00u, 0x00u, 0x00u, 0x80u), minor)
    }

    @Test
    fun beforeRank_withPrefix_prefixIsCopied() {
        val prefix = ubyteArrayOf(0x50u, 0x60u)
        val minor = buildPivotMinorWithBeforeRank(
            prefix = prefix,
            prefixLength = 2,
            pivotByte = 0xA0u,
            rank = 1,
        )
        assertContentEquals(ubyteArrayOf(0x50u, 0x60u, 0xA0u, 0x7Fu, 0x80u), minor)
    }

    @Test
    fun beforeRank_withLargeRankAndPrefix_producesCorrectLayout() {
        val prefix = ubyteArrayOf(0x50u)
        // rank=129: suffixRepeatCount=1, terminalByte=0x7F
        val minor = buildPivotMinorWithBeforeRank(
            prefix = prefix,
            prefixLength = 1,
            pivotByte = 0xA0u,
            rank = 129,
        )
        assertContentEquals(ubyteArrayOf(0x50u, 0xA0u, 0x00u, 0x7Fu, 0x80u), minor)
    }

    @Test
    fun beforeRank_consecutiveRanks_areStrictlyDecreasing() {
        val ranks = listOf(1, 2, 127, 128, 129, 130, 255, 256, 257, 500)
        val minors = ranks.map { rank ->
            buildPivotMinorWithBeforeRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
        }
        for (i in 0 until minors.lastIndex) {
            assertTrue(
                compareUByteArrays(minors[i], minors[i + 1]) > 0,
                "beforeRank(${ranks[i]}) must be > beforeRank(${ranks[i + 1]})",
            )
        }
    }

    @Test
    fun beforeRank_zeroRank_throws() {
        assertFailsWith<IllegalArgumentException> {
            buildPivotMinorWithBeforeRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = 0,
            )
        }
    }

    // ── afterRank / beforeRank symmetry ─────────────────────────────────

    @Test
    fun afterAndBeforeRanks_allTerminateWithTerminator() {
        val ranks = listOf(1, 64, 127, 128, 255, 256, 500)
        for (rank in ranks) {
            val after = buildPivotMinorWithAfterRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
            val before = buildPivotMinorWithBeforeRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
            assertTrue(
                after.last() == FractionalIndex.TERMINATOR,
                "afterRank($rank) must end with terminator",
            )
            assertTrue(
                before.last() == FractionalIndex.TERMINATOR,
                "beforeRank($rank) must end with terminator",
            )
        }
    }

    @Test
    fun afterRank_allValuesBeforePivotBoundary_areAbovePivot() {
        // Every afterRank suffix must sort after the pivot byte alone (i.e., [pivot, 0x80])
        val pivotMinor = ubyteArrayOf(0xA0u, 0x80u)
        val ranks = listOf(1, 127, 128, 254, 255, 500)
        for (rank in ranks) {
            val minor = buildPivotMinorWithAfterRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
            assertTrue(
                compareUByteArrays(minor, pivotMinor) > 0,
                "afterRank($rank) must sort after pivot minor",
            )
        }
    }

    @Test
    fun beforeRank_allValuesBeforePivotBoundary_areBelowPivot() {
        // Every beforeRank suffix must sort before the pivot byte alone (i.e., [pivot, 0x80])
        val pivotMinor = ubyteArrayOf(0xA0u, 0x80u)
        val ranks = listOf(1, 128, 129, 256, 257, 500)
        for (rank in ranks) {
            val minor = buildPivotMinorWithBeforeRank(
                prefix = ubyteArrayOf(),
                prefixLength = 0,
                pivotByte = 0xA0u,
                rank = rank,
            )
            assertTrue(
                compareUByteArrays(minor, pivotMinor) < 0,
                "beforeRank($rank) must sort before pivot minor",
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun compareUByteArrays(a: UByteArray, b: UByteArray): Int {
        val minLength = minOf(a.size, b.size)
        for (i in 0 until minLength) {
            val cmp = a[i].compareTo(b[i])
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }
}
