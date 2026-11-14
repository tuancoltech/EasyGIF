package com.nht.gif

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.drawable.toDrawable
import com.nht.gif.toolbox.Toolbox.closestEven
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropParams(
  val outW: Int,
  val outH: Int,
  val x: Int,
  val y: Int,
) : Serializable {

  constructor(rect: Rect) : this(rect.width(), rect.height(), rect.left, rect.top)

  fun toRect() = Rect(x, y, x + outW, y + outH)

  fun toFFmpegCropCommand() = "crop=$outW:$outH:$x:$y"

  fun getFFmpegScaleCommand(targetShort: Int) = "scale=${calcScaledResolutionString(targetShort, ":", false)}"

  fun crop(bitmap: Bitmap) = Bitmap.createBitmap(bitmap, x, y, outW, outH)

  /**
   * Re-maps this crop, expressed in the coordinate space of a [srcW]x[srcH] video, onto a frame of
   * [frameW]x[frameH]. The frame is decoded by a different component than the one that reported
   * the video size, so the two can disagree (scaled, anamorphic, ...). Identity when sizes match;
   * the result always lies inside the frame, so [crop] cannot throw on bounds.
   */
  fun scaledTo(frameW: Int, frameH: Int, srcW: Int, srcH: Int): CropParams {
    if (frameW == srcW && frameH == srcH) return this
    val sx = frameW / srcW.toDouble()
    val sy = frameH / srcH.toDouble()
    val newX = (x * sx).roundToInt().coerceIn(0, frameW - 1)
    val newY = (y * sy).roundToInt().coerceIn(0, frameH - 1)
    return CropParams(
      outW = (outW * sx).roundToInt().coerceIn(1, frameW - newX),
      outH = (outH * sy).roundToInt().coerceIn(1, frameH - newY),
      x = newX, y = newY,
    )
  }

  fun shortLength() = min(this.outW, this.outH)

  fun calcScaledResolution(targetShort: Int): Pair<Int, Int> {
    // According to the video encoder requirements, the length and width should be multiples of 2
    val long = max(outW, outH)
    val short = min(outW, outH)
    val targetLong = (long * targetShort / short.toDouble()).closestEven()
    return if (outW > outH) Pair(targetLong, targetShort) else Pair(targetShort, targetLong)
  }

  // useMinus2 = true will make FFmpeg calculate scaling by itself
  fun calcScaledResolutionString(targetShort: Int, splitter: String, useMinus2: Boolean) = when (useMinus2) {
    true -> if (outW > outH) "-2$splitter$targetShort" else "$targetShort$splitter-2"
    false -> calcScaledResolution(targetShort).run { "$first$splitter$second" }
  }

  fun createPlaceholderBitmap(resources: Resources) = Bitmap.createBitmap(outW, outH, Bitmap.Config.ALPHA_8).apply {
    eraseColor(Color.TRANSPARENT)
  }.toDrawable(resources)
}