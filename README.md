[![Maven Central](https://img.shields.io/maven-central/v/dev.pon/fractional-indexing?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.pon/fractional-indexing)

![JVM](https://img.shields.io/badge/-jvm-DB413D.svg?style=flat)
![Android](https://img.shields.io/badge/-android-6EDB8D.svg?style=flat)
![iOS](https://img.shields.io/badge/-ios-CDCDCD.svg?style=flat)
![macOS](https://img.shields.io/badge/-macos-111111.svg?style=flat)
![JS](https://img.shields.io/badge/-js-F8DB5D.svg?style=flat)
![wasmJS](https://img.shields.io/badge/-wasm--js-624FE8.svg?style=flat)

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

https://github.com/user-attachments/assets/37b3f98f-a8ec-45a9-ac35-c822c50be74f

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
- `FractionalIndexGenerator.rebalance(...)` accepts either-side null endpoints for open-ended generation.
- `FractionalIndexGenerator.rebalance(count, null, null)` starts from `FractionalIndex.default()`.
- `FractionalIndexGenerator.rebalance(count, lowerEndpoint, upperEndpoint)` includes non-null endpoints in the returned list.
- When both endpoints are non-null, they must define a valid ascending sequence for `count`.
- `toString()` is a debug representation. Use `toHexString()`, `toSortableBase64String()`, or `toBase64String()` for serialization.
- `toSortableBase64String()` is a **library-specific encoding** that preserves sort order. Not a standard — see [`SortableBase64`](library/src/commonMain/kotlin/dev/pon/fractionalindexing/SortableBase64.kt) for the encoding specification.
- `toBase64String()` uses standard Base64 (RFC 4648) but does **not** preserve sort order.

## Public API Overview

- `FractionalIndex.default()`
- `FractionalIndex.fromBytes(bytes)`
- `FractionalIndex.fromBytesOrThrow(bytes)`
- `FractionalIndex.fromHexString(hex)`
- `FractionalIndex.fromHexStringOrThrow(hex)`
- `FractionalIndex.fromSortableBase64String(str)`
- `FractionalIndex.fromSortableBase64StringOrThrow(str)`
- `FractionalIndex.fromBase64String(base64)`
- `FractionalIndex.fromBase64StringOrThrow(base64)`
- `FractionalIndex.bytes`
- `FractionalIndex.toHexString()`
- `FractionalIndex.toSortableBase64String()`
- `FractionalIndex.toBase64String()`
- `FractionalIndexGenerator.before(index)`
- `FractionalIndexGenerator.after(index)`
- `FractionalIndexGenerator.between(left, right)`
- `FractionalIndexGenerator.betweenOrThrow(left, right)`
- `FractionalIndexGenerator.rebalance(count, lowerEndpoint, upperEndpoint)`
- `FractionalIndexGenerator.rebalanceOrThrow(count, lowerEndpoint, upperEndpoint)`
- `FractionalIndex.before()`
- `FractionalIndex.after()`
- `FractionalIndex.between(other)`
- `FractionalIndex.betweenOrThrow(other)`

## Versioning

This library follows [Semantic Versioning](https://semver.org/).

- Changes to the key format or generation algorithm that **break compatibility with previously generated keys** are treated as breaking changes (major version bump).
- Changes that only affect the exact canonical keys produced by future calls are not considered breaking as long as existing keys remain valid and continue to sort correctly.
- Algorithmic output changes may still be called out in release notes.

**Note:** Upgrading to a new major version may require migrating your existing database records to maintain the correct sort order.

## API Compatibility Check

- CI runs `./gradlew :library:checkKotlinAbi --no-configuration-cache` to detect binary-incompatible public API changes.
- When intentionally changing public API, regenerate the baseline with `./gradlew :library:updateKotlinAbi --no-configuration-cache` and commit the updated ABI dump files under `library/api/`.

## Performance Regression Check

- Relative and absolute performance regression checks run in regular JVM tests.
- CI publishes the measured JVM perf profile and memory observation in the main test workflow so regressions are visible on PRs.
- Run the JVM perf regression test directly with:
  - `./gradlew :library:jvmTest --tests dev.pon.fractionalindexing.FractionalIndexGeneratorPerformanceRegressionTest --rerun-tasks --no-configuration-cache`
- CI also provides a dedicated perf observation workflow (`.github/workflows/perf-observation.yml`) that archives the same profile and memory snapshot.

## License

Apache License 2.0. See [LICENSE](LICENSE).
