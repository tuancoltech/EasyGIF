# Video-to-GIF Editor — Specification

## Overview

`VideoToGifActivity` lets the user trim, crop, and annotate a video before opening the export
options dialog. Playback is driven by a `VideoView` (`MediaPlayer`).

## Functional Requirements

| # | Requirement |
|---|-------------|
| F1 | After `MediaPlayer` is prepared, the editor requires a decodable video size (width > 0 and height > 0) AND a positive duration. Otherwise the input is transcoded once through `VideoToGifVideoFallbackActivity`; if the transcoded input is still unreadable, "unable to read video" is shown and the screen finishes. |

## Non-Functional Requirements

| # | Requirement |
|---|-------------|
| NF1 | The trim `RangeSlider` and the cursor `Slider` are only configured with a positive `valueTo`; Material throws on draw when `valueFrom >= valueTo`. |
| NF2 | The transcode recovery must not loop: an input that is still unreadable after transcoding (`EXTRA_FROM_FALLBACK`) is rejected instead of transcoded again. |
