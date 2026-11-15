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
| 2 | AGP 8.13.2 → 9.0.1 (Gradle 9.1.0, built-in Kotlin, new DSL). The property becomes redundant: optimized resource shrinking is on by default, so it is removed. | F3 |

## Verification

- Release APK size and content compared before and after (baseline: 25,364,470 bytes,
  `classes.dex` 3,322,364 bytes, `resources.arsc` 1,045,764 bytes, 794 `res/` entries).
- Unit tests pass.
- Manual smoke test of the release build: video to GIF, export, GIF split, text overlay.

## Step 2 — AGP 9.0.1 migration scope

Source: AGP 9.0.0 release notes and "Migrate to built-in Kotlin" (developer.android.com).

| Area | Requirement from AGP 9.0 | Change in this repo |
|---|---|---|
| Gradle | minimum 9.1.0 | Wrapper 8.13 → 9.1.0 (checksum pinned) |
| JDK / build tools | JDK 17, build tools 36.0.0 | Already satisfied (JDK 17.0.14, 36.0.0 installed) |
| Kotlin | built-in Kotlin on by default; `org.jetbrains.kotlin.android` is incompatible with the new DSL; KGP ≥ 2.2.10 | Remove the `kotlin-android` plugin (app + root + catalog). Remove the root `buildscript` KGP 1.9.22 classpath (it would downgrade KGP). KGP 2.3.20 still arrives through the Compose compiler plugin. |
| `kotlinOptions {}` | replaced by `kotlin.compilerOptions {}` | `jvmTarget` is derived from `compileOptions.targetCompatibility` (17), so the block is deleted |
| New DSL (`android.newDsl=true`) | legacy DSL names/variant API removed | `packagingOptions` → `packaging`; `archivesBaseName` → `base.archivesName`; Groovy `is*` properties verified |
| Unused library plugin | a second AGP version on the plugin classpath is invalid | Remove unused `android-library` plugin alias and its `agp-library` 8.3.1 version |
| `rootProject.buildDir` | deprecated | `layout.buildDirectory` |
| R8 defaults | `optimizedResourceShrinking`, `strictFullModeForKeepRules` default to true | Remove the now-redundant explicit flag; keep rules are empty, so strict full mode only affects library consumer rules → covered by the on-device test |

Non-goals: Kotlin/Compose/dependency version bumps, minSdk/targetSdk changes, moving to AGP 9.1+.

Rollback: revert the step 2 commit; step 1 (AGP 8.13.2) is a self-contained working state.
