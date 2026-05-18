package com.nht.gif

import com.nht.gif.MyConstants.ADD_TEXT_RENDER_PNG_PATH
import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.WebpQuality
import com.nht.gif.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.nht.gif.MyConstants.OUTPUT_GIF_TEMP_PATH
import com.nht.gif.MyConstants.OUTPUT_WEBP_TEMP_PATH
import com.nht.gif.toolbox.MediaTools.saveToPng
import com.nht.gif.toolbox.Toolbox.toEmptyStringIf
import java.io.Serializable
import kotlin.math.ceil
import kotlin.math.min

data class TaskBuilderVideoToGif(
  val inputVideoPath: String,
  /** 1 == 1ms , do not use IntRange because it is not serializable */
  val trimTime: Pair<Int, Int>?,
  val cropParams: CropParams,
  /** the resolution of the short side when outputting */
  val shortLength: Int,
  val outputSpeed: Float,
  val outputFps: Int,
  val colorQuality: Int,
  val reverse: Boolean,
  val textRender: TextRender?,
  val lossy: Int?,
  val videoWH: Pair<Int, Int>,
  val duration: Int,
  /** The interval between every loops, in centi seconds. (1 == 0.01 sec) */
  val finalDelay: Int,
  /** Color(RRGGBB), Similarity * 100 */
  val colorKey: Pair<String, Int>?,
  val outputFormat: OutputFormat = OutputFormat.GIF,
  /** null when outputFormat == GIF; MEDIUM by default when outputFormat == ANIMATED_WEBP */
  val webpQuality: WebpQuality? = null,
  val colorFilter: ExportColorFilter = ExportColorFilter.NONE,
) : Serializable {

  init {
    TextRender.render(textRender, videoWH.first, videoWH.second).saveToPng(ADD_TEXT_RENDER_PNG_PATH)
  }

  fun getForPreviewOnly() = TaskBuilderVideoToGifForPreview(shortLength, colorQuality, lossy, videoWH, colorKey, colorFilter)

  fun getOutputFramesEstimated() = ceil((trimTime?.let { it.second - it.first } ?: duration) * outputFps / outputSpeed / 1000.0).toInt()

  private fun resolutionParams(cropParams: CropParams, shortLength: Int): String {
    val short = cropParams.shortLength()
    val pixel = min(shortLength, short)
    return if (shortLength == 0 || shortLength >= short) {
      ""
    } else {
      ",scale=" + if ((cropParams.outW > cropParams.outH)) {
        "-2:$pixel"
      } else {
        "$pixel:-2"
      } + ":flags=lanczos"
    }
  }

  fun getCommandExtractFrame() =
    "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN ${trimTime?.let { "-ss ${trimTime.first}ms -to ${trimTime.second}ms " } ?: ""} -i \"$inputVideoPath\" -i \"$ADD_TEXT_RENDER_PNG_PATH\" " +
      "-filter_complex \"[0:v] setpts=PTS/$outputSpeed,fps=fps=$outputFps [0vPreprocessed]; " +
      "[0vPreprocessed][1:v] overlay=0:0," + cropParams.toFFmpegCropCommand() + resolutionParams(
      cropParams, shortLength
    ) + (colorKey?.let { ",colorkey=#${it.first}:${it.second / 100f}:0" } ?: "") + (",reverse").toEmptyStringIf { !reverse } +
      "\" \"${MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH}%06d.bmp\""

  fun getCommandCreatePalette(): String = buildPaletteCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    colorQuality = colorQuality,
    palettePath = MyConstants.PALETTE_PATH,
    colorFilter = colorFilter,
  )

  fun getCommandVideoToGif(): String = buildGifCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    palettePath = MyConstants.PALETTE_PATH,
    outputPath = OUTPUT_GIF_TEMP_PATH,
    fps = outputFps,
    finalDelay = finalDelay,
    colorFilter = colorFilter,
  )

  fun getCommandVideoToWebp(): String = buildWebpCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    outputPath = OUTPUT_WEBP_TEMP_PATH,
    fps = outputFps,
    quality = checkNotNull(webpQuality) { "webpQuality must be set when outputFormat == ANIMATED_WEBP" },
    vfChain = colorFilter.vfChain,
  )

  companion object {
    /** Pure command builders — extracted so they can be tested without Android context. */
    internal fun buildPaletteCommand(framesPath: String, colorQuality: Int, palettePath: String, colorFilter: ExportColorFilter): String {
      val vfPrefix = colorFilter.vfChain?.let { "$it," } ?: ""
      val maxColors = if (colorFilter == ExportColorFilter.VINTAGE) 64 else colorQuality
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${framesPath}%06d.bmp\" " +
        "-vf ${vfPrefix}palettegen=max_colors=${maxColors}:stats_mode=diff -y \"$palettePath\""
    }

    internal fun buildGifCommand(framesPath: String, palettePath: String, outputPath: String, fps: Int, finalDelay: Int, colorFilter: ExportColorFilter): String {
      val filterComplex = colorFilter.vfChain?.let {
        "\"[0:v]$it[v];[v][1:v]paletteuse=dither=bayer\""
      } ?: "paletteuse=dither=bayer"
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -framerate $fps " +
        "-i \"${framesPath}%06d.bmp\" -i \"$palettePath\" " +
        "-filter_complex $filterComplex -final_delay $finalDelay -y \"$outputPath\""
    }

    internal fun buildWebpCommand(framesPath: String, outputPath: String, fps: Int, quality: WebpQuality, vfChain: String? = null): String {
      val qualityFlags = if (quality.lossless) "-lossless 1" else "-quality ${quality.ffmpegQuality}"
      val vfArg = vfChain?.let { "-vf \"$it\" " } ?: ""
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -framerate $fps " +
        "-i \"${framesPath}%06d.bmp\" ${vfArg}$qualityFlags -compression_level 6 -loop 0 -y \"$outputPath\""
    }
  }
}