# Video-to-GIF Export Preview — Architecture

## Components

| Component | Responsibility |
|---|---|
| `VideoToGifExportOptionsDialogFragment` | Builds a `TaskBuilderVideoToGifForPreview` from UI state, calls `PreviewController.render`, shows/hides the spinner, and displays the result when non-null. |
| `PreviewController` | Owns the rendering pipeline, the in-memory `bitmapCache`, and the on-disk `fileExistsCache`. |
| `TaskBuilderVideoToGifForPreview` | Immutable settings key; also derives every intermediate cache file path. |

## Pipeline (run on `renderContext` = `Dispatchers.IO.limitedParallelism(1)`)

```
scale+save PNG → colorkey → color filter → palettegen → paletteuse (.gif)
   → decode base bitmap (lossy = null)
   → [lossy != null] gifsicle --lossy → decode lossy bitmap
```

Each stage is guarded by `ensureCached` (skip if already produced) and `ensureActive()` (abort if superseded).

## Failure handling

`decodeOrInvalidate(path)` decodes a stage output. If `BitmapFactory.decodeFile` returns null
(FFmpeg/gifsicle failed, the OS purged the cache dir, or decoding failed), it clears
`fileExistsCache` and returns null. `renderBlocking` then returns null without caching anything,
and `render` returns null to the Fragment, which leaves the previous preview in place.

```
render() ──► Bitmap   [pipeline succeeded; cached]
render() ──► null     [a decode failed; nothing cached; stage cache cleared → next render re-runs all stages]
```

## Frame preparation (dialog, before `PreviewController` is created)

The crop rect lives in `MediaPlayer.videoWidth/Height` space, while the frame comes from
`MediaMetadataRetriever` (or the FFmpeg fallback), whose size is not guaranteed to match. The dialog
therefore crops with `cropParams.scaledTo(frame.width, frame.height, videoW, videoH)`, which is the
identity when sizes match and otherwise scales and clamps the rect to the frame. `PreviewController`
still receives the original `cropParams`, since only its aspect ratio is used (output resolution).
