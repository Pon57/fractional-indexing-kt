[![Maven Central](https://img.shields.io/maven-central/v/dev.pon/fractional-indexing?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.pon/fractional-indexing)

# Fractional Indexing for Kotlin

A Kotlin Multiplatform library that generates lexicographically sortable keys for application-defined ordering (e.g., drag-and-drop lists).
Each key is a canonical variable-length byte sequence ending in `0x80`. By generating new keys from neighboring items (`before` / `after` / `between`), most inserts can be handled without rewriting the entire list.

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

val center = FractionalIndex.default()
val left = FractionalIndexGenerator.before(center)
val right = FractionalIndexGenerator.after(center)

val mid = FractionalIndexGenerator
    .between(left, right)
    .getOrThrow()

check(left < mid && mid < right)
```

## Example App

- A Kotlin Multiplatform example module is available at `example/`.
- Run the drag-and-drop list demo with `./gradlew :example:jvmRun`.

https://github.com/user-attachments/assets/6060a329-eb4c-44a3-8625-1ab7e67779db

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
val indexFromSortableBase64 = FractionalIndex.fromSortableBase64String("Us-").getOrThrow()
val indexFromBase64 = FractionalIndex.fromBase64String("f4A=").getOrThrow()

val hex = indexFromHex.toHexString()                                  // "7f80"
val sortableBase64 = indexFromSortableBase64.toSortableBase64String() // "Us-"
val base64 = indexFromBase64.toBase64String()                         // "f4A="
```

`fromBytes` / `fromHexString` / `fromSortableBase64String` / `fromBase64String` accept canonical library format only.
Ending with `0x80` is necessary but not sufficient: the first byte is also a format tag.
Malformed or non-canonical keys (for example `0080`, `ff80`, `0180`) return failure.

## Notes

- `FractionalIndex` values are lexicographically comparable (`Comparable<FractionalIndex>`).
- All valid indexes end with the terminator byte `0x80`.
- `FractionalIndexGenerator.between(...)` accepts bounds in either order.
- `toString()` is a debug representation. Use `toHexString()`, `toSortableBase64String()`, or `toBase64String()` for serialization.
- `toSortableBase64String()` is a **library-specific encoding** that preserves sort order. Not a standard — see [`SortableBase64`](library/src/commonMain/kotlin/dev/pon/fractionalindexing/SortableBase64.kt) for the encoding specification.
- `toBase64String()` uses standard Base64 (RFC 4648) but does **not** preserve sort order.

## Public API Overview

- `FractionalIndex.default()`
- `FractionalIndex.fromBytes(bytes)`
- `FractionalIndex.fromHexString(hex)`
- `FractionalIndex.fromSortableBase64String(str)`
- `FractionalIndex.fromBase64String(base64)`
- `FractionalIndex.bytes`
- `FractionalIndex.toHexString()`
- `FractionalIndex.toSortableBase64String()`
- `FractionalIndex.toBase64String()`
- `FractionalIndexGenerator.before(index)`
- `FractionalIndexGenerator.after(index)`
- `FractionalIndexGenerator.between(left, right)`
- `FractionalIndex.before()`
- `FractionalIndex.after()`
- `FractionalIndex.between(other)`

## Versioning

This library follows [Semantic Versioning](https://semver.org/).  
Changes to the key format or generation algorithm that **break compatibility with previously generated keys** are treated as breaking changes (major version bump).

**Note:** Upgrading to a new major version may require migrating your existing database records to maintain the correct sort order.

## API Compatibility Check

- CI runs `./gradlew :library:checkLegacyAbi --no-configuration-cache` to detect binary-incompatible public API changes.
- When intentionally changing public API, regenerate the baseline with `./gradlew :library:updateLegacyAbi --no-configuration-cache` and commit the updated ABI dump files under `library/api/`.

## Performance Regression Check

- Relative performance regression checks run in regular JVM tests.
- Strict absolute-budget checks can be run locally with:
  - `./gradlew :library:jvmTest --tests dev.pon.fractionalindexing.FractionalIndexGeneratorPerformanceRegressionTest -DfractionalIndexing.perf.strict=true --rerun-tasks --no-configuration-cache`
- CI also provides a dedicated strict workflow (`.github/workflows/perf-strict.yml`) for scheduled/manual verification.
- The strict workflow additionally publishes non-gating memory observations (peak/retained used heap) for trend tracking.

## License

Apache License 2.0. See [LICENSE](LICENSE).
