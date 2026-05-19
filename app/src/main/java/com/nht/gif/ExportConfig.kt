package com.nht.gif

import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.WebpQuality
import java.io.Serializable

/**
 * Immutable snapshot of all user-controlled export settings for a single export task.
 *
 * Groups the "how to produce the output" decisions. Source-side facts (video path, trim window,
 * crop rect, etc.) live in [VideoInput].
 *
 * @param fps Target frame rate for the output animation.
 * @param shortLength Target resolution of the short side in pixels. 0 means original resolution.
 * @param outputSpeed Playback speed multiplier applied via `setpts=PTS/<speed>`.
 * @param colorQuality Maximum palette colours for GIF encoding (1–256).
 * @param loopMode Forward, reverse, or boomerang loop applied during frame extraction.
 * @param lossy Gifsicle lossy compression level (0–200). `null` disables lossy compression.
 * @param finalDelay Interval between loops in centiseconds. (1 == 0.01 s)
 * @param colorKey Color-key chroma key: first = RRGGBB hex, second = similarity × 100.
 * @param outputFormat GIF or Animated WebP output container.
 * @param webpQuality WebP quality preset. `null` when [outputFormat] is GIF.
 * @param colorFilter Color filter preset applied during palette generation and encoding.
 * @param textRender Optional text overlay composited on every frame. `null` means no text.
 */
data class ExportConfig(
  val fps: Int,
  val shortLength: Int,
  val outputSpeed: Float,
  val colorQuality: Int,
  val loopMode: ExportLoopMode = ExportLoopMode.FORWARD,
  val lossy: Int?,
  val finalDelay: Int,
  val colorKey: Pair<String, Int>?,
  val outputFormat: OutputFormat = OutputFormat.GIF,
  val webpQuality: WebpQuality? = null,
  val colorFilter: ExportColorFilter = ExportColorFilter.NONE,
  val textRender: TextRender?,
) : Serializable
