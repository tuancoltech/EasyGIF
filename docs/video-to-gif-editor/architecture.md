# Video-to-GIF Editor — Architecture

## Readiness flow

```
onCreateIfEulaAccepted
  └─ MediaMetadataRetriever pre-flight (duration != null) ── null ──► fallback transcode
VideoView.onPrepared ──► mediaPlayerReady(mediaPlayer)
  ├─ videoWH <= 0 or duration <= 0 ── from fallback? ── yes ──► toast "unable to read video" + finish
  │                                                   └─ no  ──► VideoToGifVideoFallbackActivity (transcode) + finish
  └─ otherwise: configure cropImageView, rangeSlider, sliderCursor, enable Save
```

The pre-flight uses `MediaMetadataRetriever`, a different component from the `MediaPlayer` behind
`VideoView`; the two can disagree, so `mediaPlayerReady` re-validates what `MediaPlayer` reports
(size and duration) before any dependent view is configured. Teardown is posted to the next UI
message because releasing the player inside `onPrepared` would crash `VideoView`'s own
`getVideoWidth()` call that runs right after the callback.
