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

## Usage

```kotlin
import dev.pon.fractionalindexing.FractionalIndex
import dev.pon.fractionalindexing.FractionalIndexGenerator

val center = FractionalIndex.default()
val left = FractionalIndexGenerator.before(center)
val right = FractionalIndexGenerator.after(center)

val mid = FractionalIndexGenerator
    .between(left, right, FractionalIndexGenerator.BetweenStrategy.SPREAD)
    .getOrThrow()
```

### Parse/Encode

```kotlin
val fromHex = FractionalIndex.fromHexString("7f80").getOrThrow()
val fromBase64 = FractionalIndex.fromBase64String("f4A=").getOrThrow()

val hex = fromHex.toHexString()
val base64 = fromBase64.toBase64String()
```

## API overview

- `FractionalIndex.default()`
- `FractionalIndex.fromBytes(...)`
- `FractionalIndex.fromHexString(...)`
- `FractionalIndex.fromBase64String(...)`
- `FractionalIndexGenerator.before(index)`
- `FractionalIndexGenerator.after(index)`
- `FractionalIndexGenerator.between(left, right, strategy)`

## License

Apache License 2.0. See [LICENSE](LICENSE).
