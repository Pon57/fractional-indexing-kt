# Fractional Indexing for Kotlin

Kotlin Multiplatform library for generating sortable fractional index keys.

## Coordinates

- Group: `dev.pon`
- Artifact: `fractional-indexing`

## Installation

```kotlin
dependencies {
    implementation("dev.pon:fractional-indexing:<version>")
}
```

## Quick Start

```kotlin
import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator

val center = FractionalIndex.default()      // 80
val left = FractionalIndexGenerator.before(center)
val right = FractionalIndexGenerator.after(center)

val mid = FractionalIndexGenerator
    .between(left, right)                   // default strategy: SPREAD
    .getOrThrow()                           // between returns Result<FractionalIndex>

check(left < mid && mid < right)
```

## Extension API

```kotlin
import dev.pon.fractionalindexing.after
import dev.pon.fractionalindexing.before
import dev.pon.fractionalindexing.between

val center = FractionalIndex.default()
val left = center.before()
val right = center.after()
val mid = left.between(right).getOrThrow()
```

## Parse and Encode

```kotlin
val indexFromHex = FractionalIndex.fromHexString("7f80").getOrThrow()
val indexFromBase64 = FractionalIndex.fromBase64String("f4A=").getOrThrow()

val hex = indexFromHex.toHexString()          // "7f80"
val base64 = indexFromBase64.toBase64String() // "f4A="
```

## Notes

- `FractionalIndex` values are lexicographically comparable (`Comparable<FractionalIndex>`).
- All valid indexes end with the terminator byte `0x80`.
- `FractionalIndexGenerator.between(...)` accepts bounds in either order.
- `BetweenStrategy.SPREAD` is the default for `between`.
- `toString()` is a debug representation. Use `toHexString()` or `toBase64String()` for serialization.

## Between Strategies

`between(left, right, strategy)` supports two strategies:

- `SPREAD` (default): tends to distribute generated keys across available space to reduce key-length growth in common repeated-insert patterns.
- `MINIMAL`: generates the shortest locally valid next key (compatible with classic minimal midpoint behavior).

Use `SPREAD` for general-purpose usage. Choose `MINIMAL` when you need behavior closest to minimal midpoint generation.

## Public API Overview

- `FractionalIndex.default()`
- `FractionalIndex.fromBytes(bytes)`
- `FractionalIndex.fromHexString(hex)`
- `FractionalIndex.fromBase64String(base64)`
- `FractionalIndex.bytes`
- `FractionalIndex.toHexString()`
- `FractionalIndex.toBase64String()`
- `FractionalIndexGenerator.before(index)`
- `FractionalIndexGenerator.after(index)`
- `FractionalIndexGenerator.between(left, right, strategy)`
- `FractionalIndex.before()`
- `FractionalIndex.after()`
- `FractionalIndex.between(other, strategy)`

## License

Apache License 2.0. See [LICENSE](LICENSE).
