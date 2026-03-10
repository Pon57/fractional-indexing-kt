# Changelog

## [v2.0.1](https://github.com/Pon57/fractional-indexing-kt/compare/v2.0.0...v2.0.1) - 2026-03-10
### Bug Fixes
- fix: use defensive copy for shared DEFAULT_MINOR by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/60
### General Changes
- docs: clarify versioning policy and v2 changelog notes by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/53
- docs: refine release note category titles by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/54
- refactor: replace per-file @OptIn(ExperimentalUnsignedTypes) with module-level opt-in by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/55
- perf: cache hashCode, short-circuit compareTo, and intern default index by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/57
- refactor: consolidate rebalance threshold constants into single object by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/56
- perf: cache FractionalIndex.default() as a singleton by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/58
- perf: add encodedLength property to FractionalIndex by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/59
- perf: zero-copy owned-minor construction in core insert paths by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/61
- chore: remove unused kotest Gradle plugin to fix test api warning by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/64
- perf: allocation-free compact minor estimation in scoring by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/63
- perf: eliminate Pair allocations in parse path by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/62
- Fix hashCode performance regression with a manual lazy cache by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/65
- Revert "perf: cache FractionalIndex.default() as a singleton" by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/66
### Dependency Updates
- fix(deps): update kotest to v6.1.5 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/51

## [v2.0.0](https://github.com/Pon57/fractional-indexing-kt/compare/v1.2.0...v2.0.0) - 2026-03-08
### Breaking Changes
- fix!: use compact successor after FractionalIndex.default() by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/49
  - `after(FractionalIndex.default())` now returns the compact successor `8080`. In v1, the first key generated after `FractionalIndex.default()` was different.
    - This also affects nearby `before(...)`, `between(...)`, and `rebalance(...)` results around the default key
    - Existing canonical keys remain valid and continue to sort correctly
- Redesign rebalance with multi-strategy optimization by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/50
  - `FractionalIndexGenerator.rebalance(...)` has been redesigned around optional inclusive endpoints instead of exclusive bounds.
    - Parameters were renamed from `lowerExclusive` / `upperExclusive` to `lowerEndpoint` / `upperEndpoint`
    - Non-null endpoints are now included in the returned list
    - When both endpoints are present, `count` now means the total number of returned keys, including the endpoints
    - Reversed endpoints are now invalid; callers must pass an ascending range
    - Exact rebalance output may differ from v1 because v2 uses a new optimization pipeline that prefers shorter layouts in major-gap, minor-gap, zero-major, pivot, and boundary cases
> [!NOTE]
> The compact successor change was conservatively listed as breaking at the time. Under the versioning policy now clarified in the README, this change alone would not require a major bump, as existing keys remain valid. See [Versioning](README.md#versioning).
### Migration Notes
- If you previously used `rebalance(count, lower, upper)` to generate `count` interior keys, change it to request the new total count including both endpoints
- If you used named arguments, rename them to `lowerEndpoint` / `upperEndpoint`
- If you passed bounds in arbitrary order, normalize them before calling `rebalance`
- If you assert exact generated keys in tests, especially around `FractionalIndex.default()`, update the expected values for v2
### Dependency Updates
- chore(deps): update dependency com.android.kotlin.multiplatform.library to v9.1.0 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/46
- chore(deps): update gradle to v9.4.0 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/48

## [v1.2.0](https://github.com/Pon57/fractional-indexing-kt/compare/v1.1.0...v1.2.0) - 2026-03-02
### General Changes
- feat: add rebalance API with deterministic in-range key redistribution by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/44

## [v1.1.0](https://github.com/Pon57/fractional-indexing-kt/compare/v1.0.1...v1.1.0) - 2026-03-01
### General Changes
- feat: add OrThrow APIs by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/42

## [v1.0.1](https://github.com/Pon57/fractional-indexing-kt/compare/v1.0.0...v1.0.1) - 2026-03-01
### General Changes
- test: add benchmark quality regression tests by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/36
- test: add relative and strict performance regression checks by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/38
- ci: pin upload-artifact action in perf-strict workflow by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/40
- refactor: split generator core and tests into internal modules by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/41

## [v1.0.0](https://github.com/Pon57/fractional-indexing-kt/compare/v0.0.4...v1.0.0) - 2026-02-27
### Breaking Changes
- feat!: redesign key generation with tiered encoding by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/28
### General Changes
- build: enable Kotlin ABI validation and ABI check in CI by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/22
- ci: run test workflow tasks at repository scope by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/23
- chore: add KMP drag-and-drop sample app by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/24
- docs: add Maven Central badge to README.md by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/25
- chore: configure renovate automerge for patch updates by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/29
- chore: configure project settings by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/30
- docs: update demo video and fix example jvmRun task by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/31
- feat: add codec parameter to Base64 encode/decode by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/32
- feat: add sortable Base64 encoding that preserves sort order by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/33
- docs: add versioning policy and remove pre-v1.0.0 warning by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/34
- perf: reduce object allocations in between() hot path by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/35
### Dependency Updates
- chore(deps): update gradle/actions action to v5.0.2 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/20
- fix(deps): update kotest to v6.1.4 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/26
- chore(deps): update songmu/tagpr action to v1.17.1 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/27

## [v0.0.4](https://github.com/Pon57/fractional-indexing-kt/compare/v0.0.3...v0.0.4) - 2026-02-23
### General Changes
- ci: rename MavenCentral labels to Maven Central in publish workflow by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/18

## [v0.0.3](https://github.com/Pon57/fractional-indexing-kt/compare/v0.0.2...v0.0.3) - 2026-02-23
### General Changes
- ci: add Gradle cache to tagpr workflow by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/12
- Ci workflow tuning by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/14
- Ci workflow tuning by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/15
- Ci workflow tuning by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/16
- ci: switch publish workflow to publishAndReleaseToMavenCentral by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/17

## [v0.0.2](https://github.com/Pon57/fractional-indexing-kt/compare/v0.0.1...v0.0.2) - 2026-02-22
### General Changes
- Add test workflow by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/3
- Configure tagpr to auto-update JS/Wasm lockfiles after version bumps by @Pon57 in https://github.com/Pon57/fractional-indexing-kt/pull/5
- chore: Configure Renovate by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/6
### Dependency Updates
- chore(deps): update gradle/actions action to v4.4.4 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/7
- chore(deps): update actions/checkout action to v6 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/8
- chore(deps): update gradle/actions action to v5 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/11
- chore(deps): update actions/setup-java action to v5 by @renovate[bot] in https://github.com/Pon57/fractional-indexing-kt/pull/10

## [v0.0.1](https://github.com/Pon57/fractional-indexing-kt/commits/v0.0.1) - 2026-02-22
