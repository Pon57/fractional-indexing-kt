package dev.pon.fractionalindexing

import dev.pon.fractionalindexing.internal.FractionalIndexGeneratorCore

/**
 * Generates new [FractionalIndex] keys relative to existing ones.
 *
 * See also the extension functions [before], [after], and [between] on [FractionalIndex].
 */
@OptIn(ExperimentalUnsignedTypes::class)
public object FractionalIndexGenerator {
    /** Returns a new index that sorts **before** [index]. */
    public fun before(index: FractionalIndex): FractionalIndex = FractionalIndexGeneratorCore.before(index)

    /** Returns a new index that sorts **after** [index]. */
    public fun after(index: FractionalIndex): FractionalIndex = FractionalIndexGeneratorCore.after(index)

    /**
     * Returns a new index that sorts strictly between [first] and [second].
     *
     * The two bounds may be passed in either order; they just must be distinct.
     */
    public fun between(
        first: FractionalIndex,
        second: FractionalIndex,
    ): Result<FractionalIndex> = FractionalIndexGeneratorCore.between(first, second)

    /**
     * Returns a new index that sorts strictly between [first] and [second], throwing on invalid bounds.
     *
     * The two bounds may be passed in either order; they just must be distinct.
     */
    @Throws(IllegalArgumentException::class)
    public fun betweenOrThrow(
        first: FractionalIndex,
        second: FractionalIndex,
    ): FractionalIndex = FractionalIndexGeneratorCore.betweenOrThrow(first, second)

    /**
     * Rebalances [count] keys within optional exclusive bounds.
     *
     * If [count] is `0`, returns an empty list without validating bounds.
     * If both bounds are non-null, keys are strictly between them
     * (bounds may be in either order, but must be distinct).
     * Otherwise, generates an open-ended sequence;
     * if both bounds are null, it starts from [FractionalIndex.default].
     * When mixing with an existing keyspace, provide explicit bounds
     * (or handle collisions) to avoid key conflicts.
     */
    public fun rebalance(
        count: Int,
        lowerExclusive: FractionalIndex?,
        upperExclusive: FractionalIndex?,
    ): Result<List<FractionalIndex>> = FractionalIndexGeneratorCore.rebalance(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )

    /**
     * Rebalances [count] keys within optional exclusive bounds, throwing on invalid arguments.
     *
     * If [count] is `0`, returns an empty list without validating bounds.
     * If both bounds are non-null, keys are strictly between them
     * (bounds may be in either order, but must be distinct).
     * Otherwise, generates an open-ended sequence;
     * if both bounds are null, it starts from [FractionalIndex.default].
     * When mixing with an existing keyspace, provide explicit bounds
     * (or handle collisions) to avoid key conflicts.
     */
    @Throws(IllegalArgumentException::class)
    public fun rebalanceOrThrow(
        count: Int,
        lowerExclusive: FractionalIndex?,
        upperExclusive: FractionalIndex?,
    ): List<FractionalIndex> = FractionalIndexGeneratorCore.rebalanceOrThrow(
        count = count,
        lowerExclusive = lowerExclusive,
        upperExclusive = upperExclusive,
    )
}
