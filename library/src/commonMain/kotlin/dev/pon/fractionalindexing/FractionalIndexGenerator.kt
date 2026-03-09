package dev.pon.fractionalindexing

import dev.pon.fractionalindexing.internal.FractionalIndexGeneratorCore

/**
 * Generates new [FractionalIndex] keys relative to existing ones.
 *
 * See also the extension functions [before], [after], and [between] on [FractionalIndex].
 */
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
     * Rebalances a sequence of [count] keys around optional endpoints.
     *
     * If [count] is `0`, returns an empty list without validating endpoints.
     * If both endpoints are non-null, they are included in the returned list and must define
     * a valid ascending sequence for [count].
     * If only one endpoint is provided, it is included at the corresponding edge and the rest are generated outward.
     * If both endpoints are null, it generates an open-ended sequence starting from [FractionalIndex.default].
     */
    public fun rebalance(
        count: Int,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ): Result<List<FractionalIndex>> = FractionalIndexGeneratorCore.rebalance(
        count = count,
        lowerEndpoint = lowerEndpoint,
        upperEndpoint = upperEndpoint,
    )

    /**
     * Rebalances a sequence of [count] keys around optional endpoints, throwing on invalid arguments.
     *
     * If [count] is `0`, returns an empty list without validating endpoints.
     * If both endpoints are non-null, they are included in the returned list and must define
     * a valid ascending sequence for [count].
     * If only one endpoint is provided, it is included at the corresponding edge and the rest are generated outward.
     * If both endpoints are null, it generates an open-ended sequence starting from [FractionalIndex.default].
     */
    @Throws(IllegalArgumentException::class)
    public fun rebalanceOrThrow(
        count: Int,
        lowerEndpoint: FractionalIndex?,
        upperEndpoint: FractionalIndex?,
    ): List<FractionalIndex> = FractionalIndexGeneratorCore.rebalanceOrThrow(
        count = count,
        lowerEndpoint = lowerEndpoint,
        upperEndpoint = upperEndpoint,
    )
}
