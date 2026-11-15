# Release Build Optimization — API

No public API change. The only effects are build-time: AGP/Gradle versions and one `gradle.properties`
flag. App behavior is unchanged; the release APK may contain fewer unreferenced resources.

## Checklist

- [x] Baseline release APK measured (25,364,470 bytes)
- [x] Docs written
- [x] Step 1: AGP 8.13.2 + Gradle 8.13 (wrapper checksum updated) + `android.r8.optimizedResourceShrinking=true`
- [x] Step 1: unit tests pass
- [x] Step 1: release APK rebuilt and compared with the baseline (see Results)
- [x] Step 1: on-device smoke test of the release build (Galaxy A54, Android 16; see On-device results)
- [x] Step 1 committed and pushed (`build/r8-optimized-resource-shrinking`)
- [x] Step 2: AGP 9.0.1 + Gradle 9.1.0 (branch `build/agp-9-0-1`)
- [x] Step 2: built-in Kotlin, new DSL, root build file cleanup
- [x] Step 2: build is free of AGP deprecation warnings (`--warning-mode all`: none)
- [x] Step 2: unit tests pass (79/79)
- [x] Step 2: release APK compared with step 1 (size, `resources.arsc`, `res/` entries)
- [x] Step 2: R8 `usage.txt` checked; no unexpected removals
- [x] Step 2: on-device smoke test of the release build (Galaxy A54, Android 16)

## Results (step 1)

| Build | APK bytes | `classes.dex` | `resources.arsc` | `res/` entries |
|---|---|---|---|---|
| Baseline (AGP 8.9.1) | 25,364,470 | 3,322,364 | 1,045,764 | 794 |
| AGP 8.13.2, flag off | 25,356,536 | 3,314,796 | 1,045,764 | 794 |
| AGP 8.13.2, flag on | 25,088,806 | 3,254,356 | 880,604 | 697 |

The flag accounts for about 268 KB (-1.1%) of the saving; the AGP bump alone about 8 KB. The APK is dominated
by the FFmpeg native libraries, which resource shrinking does not touch. Of 237 app-owned resources, 3 became
unreachable (`gif_saved_to_gallery`, `qq_group_id`, `join_qq_group_toast`); the last two were referenced only
from `Toolbox.cmivJoinQqGroupLambda`, which has no callers and was removed by R8 as dead code.

## On-device results (step 1, release build, Galaxy A54, Android 16)

Release APK (AGP 8.13.2 + optimized resource shrinking) installed and driven by hand; the app log was
filtered by process ID and showed no fatal exception and no resource, class or method-not-found errors.

| Area | Result |
|---|---|
| Launch, EULA, home screen, About dialog | Renders correctly (theme, icons, toggles, links) |
| Video to GIF: editor, trim sliders, ratio sheet (Square) | Works; slider configured from the video duration |
| Export dialog + preview (GIF) | Works, including crop remapping and re-render after changing clarity/quality/resolution |
| Color key ("Remove background") | Works; keyed color becomes transparent in the preview |
| Text overlay | Works; carried into the export preview |
| GIF export | Valid GIF: 256x144, 60 frames, 6.0 s; size matches the estimate |
| WebP export (with color key) | Valid animated WebP: 16 frames, alpha channel present |
| GIF Split | 16 frames loaded; saved frame 5 is pixel-identical to the source frame (SSIM 1.0) |
| GIF to Video | Valid H.264: 240x160, 16 frames, 2.0 s |

Not covered: Motion Photo to GIF (no motion-photo sample), the playback-speed sheet, and Android versions
other than 16. Observed on both the baseline and this build, so not caused by this change: with "Remove
background" expanded, the dialog's Close/Export buttons are clipped at the bottom edge (still tappable).

## Results (step 2: AGP 9.0.1, Gradle 9.1.0)

| Metric | Step 1 (AGP 8.13.2) | Step 2 (AGP 9.0.1) |
|---|---|---|
| Release APK | 25,088,806 B | 25,044,604 B (−44,202 B) |
| `classes.dex` | 3,254,356 B | 3,214,700 B |
| `resources.arsc` | 880,604 B | 880,604 B (identical) |
| `res/` entries | 697 | 697 (identical) |
| Output file name | `EasyGIF_2026.7.1_202607001-release.apk` | unchanged |
| Deprecation warnings (`--warning-mode all`) | n/a | none |

R8 usage diff (class-level): 186 classes removed only in step 2, 208 removed only in step 1 (16,654 vs 16,632 total);
the app classes newly removed are interfaces merged into their single implementation (`FileSizeEstimator`, `FilterThumbGenerator`, `SmartTrimDetector`) and
FFmpegKit's `Session` interface. JNI-facing `FFmpegKitConfig`/`AbiDetect` stay kept by the AAR's `proguard.txt`.

## On-device results (step 2, release build, Galaxy A54, Android 16)

| Flow | Result |
|---|---|
| Launch / home screen | Renders; version 2026.7.1 |
| Video to GIF editor | Plays, pause, timeline, Ratio sheet → Square crop overlay, Text overlay |
| Export dialog + preview | Square-cropped preview with text; estimate updates on resolution/clarity change |
| GIF export (144P, Low) | Valid GIF 144×144, 60 frames, 6.0 s; 171,813 B vs ~176 KB estimate |
| WebP export | Valid animated WebP 144×144, 60 frames |
| WebP + Remove background (color key) | Preview shows keyed band; exported frame has 3,426/20,736 transparent px (plain export: 0) |
| GIF Split | 16 frames extracted; saved frame 5 is SSIM 1.000 vs source, 240×160 |
| GIF to Video | Valid H.264 240×160, 16 frames, 2.0 s |
| Logcat | 0 crash-buffer entries for `com.nht.gif`; no ClassNotFound/NoSuchMethod/Resources$NotFound |

Not covered: Motion Photo to GIF (no sample file), playback-speed sheet, Android versions other than 16.
