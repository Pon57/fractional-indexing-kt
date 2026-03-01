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
}
