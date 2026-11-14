# Video-to-GIF Editor — API

No public API change. `VideoToGifActivity.start(context, inputVideoPath, fromFallback)` and the
`EXTRA_VIDEO_PATH` / `EXTRA_FROM_FALLBACK` extras are unchanged. The only behavioral difference is
internal: an input for which `MediaPlayer` reports a duration of 0 ms is now handled by the existing
transcode-or-reject recovery (see `spec.md` F1) instead of crashing when the sliders are drawn.
