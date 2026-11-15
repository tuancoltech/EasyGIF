# Release Build Optimization — Architecture

## Build configuration

| Setting | Location | Value |
|---|---|---|
| AGP | `gradle/libs.versions.toml` (`agp`) | 8.13.2 (step 1), 9.0.1 (step 2) |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` | 8.13 (step 1), 9.1.0 (step 2) |
| R8 full mode | `gradle.properties` | `android.enableR8.fullMode=true` |
| Optimized resource shrinking | `gradle.properties` | `android.r8.optimizedResourceShrinking=true` in step 1; removed in step 2 (default from AGP 9.0) |
| Release shrinking | `app/build.gradle` | `minifyEnabled true`, `shrinkResources true`, `proguard-android-optimize.txt` |

## Why resources are safe to shrink here

The app has no reflective resource lookup (`Resources.getIdentifier` and similar) and no app-level
keep rules (`app/proguard-rules.pro` has none). Libraries (Material, Glide, MMKV, the image cropper)
ship their own consumer rules. The bundled `libs/ffmpeg-kit.aar` and `libgifsicle.so` are native and
are not touched by resource shrinking.

## Plugin topology after step 2

```
root build.gradle        plugins { android.application (apply false), compose.compiler (apply false) }
app/build.gradle         plugins { android.application, compose.compiler }
                         Kotlin compilation: AGP built-in Kotlin (no kotlin-android plugin)
Kotlin Gradle plugin     resolved transitively via the Compose compiler plugin (2.3.20, >= AGP 9's 2.2.10 floor)
```

The root `buildscript { classpath kotlin-gradle-plugin:1.9.22 }` is removed: AGP 9 requires KGP ≥ 2.2.10
at runtime, and a 1.9.22 classpath entry would pin KGP below that floor.
