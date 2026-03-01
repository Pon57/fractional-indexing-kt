package dev.pon.fractionalindexing

/** Returns a new index that sorts **before** this one. Shorthand for [FractionalIndexGenerator.before]. */
@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.before(): FractionalIndex = FractionalIndexGenerator.before(this)

/** Returns a new index that sorts **after** this one. Shorthand for [FractionalIndexGenerator.after]. */
@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.after(): FractionalIndex = FractionalIndexGenerator.after(this)

/**
 * Returns a new index that sorts strictly between this and [other].
 *
 * The two bounds may be passed in either order; they just must be distinct.
 * Shorthand for [FractionalIndexGenerator.between].
 */
@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.between(
    other: FractionalIndex,
): Result<FractionalIndex> = FractionalIndexGenerator.between(this, other)

/**
 * Returns a new index that sorts strictly between this and [other], throwing on invalid bounds.
 *
 * The two bounds may be passed in either order; they just must be distinct.
 * Shorthand for [FractionalIndexGenerator.betweenOrThrow].
 */
@OptIn(ExperimentalUnsignedTypes::class)
@Throws(IllegalArgumentException::class)
public fun FractionalIndex.betweenOrThrow(
    other: FractionalIndex,
): FractionalIndex = FractionalIndexGenerator.betweenOrThrow(this, other)
