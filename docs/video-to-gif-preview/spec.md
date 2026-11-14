# Video-to-GIF Export Preview — Specification

## Overview

The export-options dialog (`VideoToGifExportOptionsDialogFragment`) shows a static preview of the
GIF that the current settings (resolution, color quality, lossy level, color key, color filter)
would produce. `PreviewController` renders that preview off the main thread.

## Functional Requirements

| # | Requirement |
|---|-------------|
| F1 | Render a preview bitmap for a given `TaskBuilderVideoToGifForPreview` from a single pre-composited video frame. |
| F2 | Reuse intermediate outputs across renders; only stages invalidated by a setting change re-run. |
| F3 | A superseded render aborts at the next stage boundary (cooperative cancellation). |
| F4 | If the preview cannot be produced (a stage failed, or its output is missing/unreadable), the render reports failure instead of throwing, and the dialog keeps showing the previous preview. |
| F5 | After a failure, the next render re-runs every stage rather than trusting cached stage outputs. |
| F6 | The preview cache directory is wiped on construction and on `clear()`. |
| F7 | The crop rect (in `MediaPlayer` video coordinates) is remapped onto the preview frame before cropping, so a frame whose size differs from the reported video size never makes cropping throw. Identity when the sizes match. |

## Non-Functional Requirements

| # | Requirement |
|---|-------------|
| NF1 | All file I/O, FFmpeg and gifsicle work runs off the main thread on a single-threaded dispatcher, so caches and temp files are never accessed concurrently. |
| NF2 | A failed render must never crash the app and must never leave a null/invalid entry in the in-memory bitmap cache. |
