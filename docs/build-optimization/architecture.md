# Release Build Optimization — Architecture

## Build configuration

| Setting | Location | Value |
|---|---|---|
| AGP | `gradle/libs.versions.toml` (`agp`) | 8.13.2 (step 1), 9.0.x (step 2) |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` | 8.13 (step 1), 9.1+ (step 2) |
| R8 full mode | `gradle.properties` | `android.enableR8.fullMode=true` |
| Optimized resource shrinking | `gradle.properties` | `android.r8.optimizedResourceShrinking=true` (AGP 8.12/8.13 only; default from AGP 9.0) |
| Release shrinking | `app/build.gradle` | `minifyEnabled true`, `shrinkResources true`, `proguard-android-optimize.txt` |

## Why resources are safe to shrink here

The app has no reflective resource lookup (`Resources.getIdentifier` and similar) and no app-level
keep rules (`app/proguard-rules.pro` has none). Libraries (Material, Glide, MMKV, the image cropper)
ship their own consumer rules. The bundled `libs/ffmpeg-kit.aar` and `libgifsicle.so` are native and
are not touched by resource shrinking.
