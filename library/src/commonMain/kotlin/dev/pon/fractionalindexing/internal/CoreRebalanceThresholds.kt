package dev.pon.fractionalindexing.internal

internal object RebalanceThresholds {
    // Below this count, the optimized result is compared against the balanced fallback and
    // the better profile wins. Above it, the optimized result is accepted directly because
    // the fallback's recursive candidate exploration becomes too expensive for diminishing
    // returns — in practice the optimized strategies already dominate at larger counts.
    const val OPTIMIZED_VS_BALANCED_FALLBACK = 32

    // The recursive pivot candidate only wins in very tight windows. Cap it so large
    // rebalances do not pay to materialize extra candidate lists just to confirm that.
    const val SINGLE_BYTE_PIVOT_RECURSIVE_CANDIDATE = 32

    // The compact-frontier candidate enumerates whole profile alternatives, so keep it
    // to the same tight-range budget as the recursive single-pivot path.
    const val ZERO_MAJOR_COMPACT_FRONTIER_CANDIDATE = 32

    // One-sided major-gap candidates help preserve shorter follow-up insertions near
    // the boundaries, but only matter in relatively small windows.
    const val MAJOR_GAP_EDGE_CANDIDATE = 32

    // Wide same-major byte gaps can look good under simple even spacing but still
    // lose immediate headroom against the midpoint-recursive layout.
    const val MINOR_GAP_BALANCED_CANDIDATE = 32

    // Cross-terminator windows can hide shorter recursive layouts that are invisible
    // to the immediate-byte capacity estimate around 0x80, so try every split while
    // the rebalance is still small enough to keep candidate explosion bounded.
    const val TERMINATOR_PIVOT_SPLIT_CANDIDATE = 32
}
