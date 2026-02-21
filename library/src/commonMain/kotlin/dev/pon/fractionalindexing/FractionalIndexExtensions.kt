package dev.pon.fractionalindexing

@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.before(): FractionalIndex = FractionalIndexGenerator.before(this)

@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.after(): FractionalIndex = FractionalIndexGenerator.after(this)

@OptIn(ExperimentalUnsignedTypes::class)
public fun FractionalIndex.between(
    other: FractionalIndex,
    strategy: FractionalIndexGenerator.BetweenStrategy = FractionalIndexGenerator.BetweenStrategy.SPREAD,
): Result<FractionalIndex> = FractionalIndexGenerator.between(this, other, strategy)
