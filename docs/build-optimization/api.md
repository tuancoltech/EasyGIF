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
- [ ] Step 2: AGP 9.0.x migration (separate approval)

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
