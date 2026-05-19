package com.nht.gif

import java.io.Serializable

/**
 * Immutable snapshot of the source video facts for a single export task.
 *
 * Groups the "what to process" inputs that describe the source material and the user's trim/crop
 * choices. Export-side decisions (speed, format, quality, text overlay, etc.) live in [ExportConfig].
 *
 * @param path Absolute path to the source video file.
 * @param trimTime Trim window in milliseconds (start to end). `null` means use the full video.
 *   Stored as [Pair] rather than [IntRange] because [IntRange] is not [Serializable].
 * @param videoWH Original width and height of the source video in pixels.
 * @param duration Total duration of the source video in milliseconds.
 * @param cropParams Crop rectangle and output dimensions derived from the user's crop gesture.
 */
data class VideoInput(
  val path: String,
  val trimTime: Pair<Int, Int>?,
  val videoWH: Pair<Int, Int>,
  val duration: Int,
  val cropParams: CropParams,
) : Serializable
