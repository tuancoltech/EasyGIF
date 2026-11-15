# Release Build Optimization — Specification

## Overview

Play Console reports that the app's R8 configuration is not using optimized resource shrinking and
recommends AGP 9.0 or higher. R8 treats code and resources as one reference graph in this mode, so
resources referenced only from removed code are dropped as well.

## Requirements

| # | Requirement |
|---|-------------|
| F1 | Release builds use R8 optimized resource shrinking. |
| F2 | The release build keeps `minifyEnabled`, `shrinkResources`, `proguard-android-optimize.txt` and R8 full mode. |
| F3 | The Play Console recommendation ("upgrade AGP to 9.0 or higher") is cleared. |
| NF1 | No runtime regression: every resource used by the app at runtime is retained. |
| NF2 | Each step is independently revertible: tooling upgrades are not mixed with behavior flags. |

## Plan

| Step | Change | Satisfies |
|---|---|---|
| 1 | AGP 8.9.1 → 8.13.2, Gradle 8.11.1 → 8.13, `android.r8.optimizedResourceShrinking=true` | F1, F2, NF1, NF2 |
| 2 | AGP 8.13.2 → 9.0.x (Gradle 9.1+, built-in Kotlin, new DSL). The property becomes redundant: optimized resource shrinking is on by default. | F3 |

## Verification

- Release APK size and content compared before and after (baseline: 25,364,470 bytes,
  `classes.dex` 3,322,364 bytes, `resources.arsc` 1,045,764 bytes, 794 `res/` entries).
- Unit tests pass.
- Manual smoke test of the release build: video to GIF, export, GIF split, text overlay.
