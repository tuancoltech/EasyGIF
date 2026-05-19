package com.nht.gif

import com.nht.gif.MyConstants.ADD_TEXT_RENDER_PNG_PATH
import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.WebpQuality
import com.nht.gif.MyConstants.FFMPEG_COMMAND_PREFIX_FOR_ALL_AN
import com.nht.gif.MyConstants.OUTPUT_GIF_TEMP_PATH
import com.nht.gif.MyConstants.OUTPUT_WEBP_TEMP_PATH
import com.nht.gif.toolbox.MediaTools.saveToPng
import java.io.Serializable
import kotlin.math.ceil
import kotlin.math.min

/**
 * Immutable data class that holds all export parameters for a video-to-GIF (or Animated WebP)
 * conversion task and acts as a command builder for each stage of the FFmpeg pipeline.
 *
 * Construction triggers an [init] block that renders the optional [textRender] overlay to a PNG
 * file on disk; the resulting PNG is then referenced by every subsequent command produced by this
 * class. Because of this side-effect, the pure command-builder logic lives in [Companion] static
 * functions so it can be tested independently without touching the filesystem.
 */
data class TaskBuilderVideoToGif(
  /** Absolute path to the source video file passed to FFmpeg as the primary input. */
  val inputVideoPath: String,
  /** Trim window in milliseconds: first = start, second = end. `null` means no trim (use the full
   *  video). Stored as [Pair] rather than [IntRange] because [IntRange] is not [Serializable]. */
  val trimTime: Pair<Int, Int>?,
  /** Crop rectangle and output dimensions derived from the user's crop gesture. */
  val cropParams: CropParams,
  /** Target resolution of the short side in pixels. 0 means "use original resolution". */
  val shortLength: Int,
  /** Playback speed multiplier applied via `setpts=PTS/<speed>` (e.g. 2.0 = double speed). */
  val outputSpeed: Float,
  /** Target frame rate for the output animation, passed as `fps=fps=<value>` in the filter chain. */
  val outputFps: Int,
  /** Maximum number of palette colors for GIF encoding (1–256). Higher values yield better colour
   *  fidelity at the cost of file size. */
  val colorQuality: Int,
  /** Loop mode applied during frame extraction: [ExportLoopMode.FORWARD] plays frames as-is,
   *  [ExportLoopMode.REVERSE] reverses them, and [ExportLoopMode.BOOMERANG] appends a reversed
   *  copy so the animation loops back and forth. */
  val loopMode: ExportLoopMode = ExportLoopMode.FORWARD,
  /** Whether the trim end point was adjusted by Smart Trim detection. Stored for provenance;
   *  the actual trim is already baked into [trimTime] by the time this object is constructed. */
  val smartTrim: Boolean = false,
  /** Optional text overlay rendered on every frame. `null` means no text is composited. */
  val textRender: TextRender?,
  /** Lossy compression level forwarded to gifsicle post-processing (0–200). `null` disables
   *  lossy compression and produces a lossless GIF. */
  val lossy: Int?,
  /** Original width and height of the source video in pixels, used to compute the text overlay
   *  canvas size and to drive crop/scale calculations. */
  val videoWH: Pair<Int, Int>,
  /** Total duration of the source video in milliseconds, used as the fallback end point when
   *  [trimTime] is `null`. */
  val duration: Int,
  /** The interval between every loops, in centi seconds. (1 == 0.01 sec) */
  val finalDelay: Int,
  /** Color(RRGGBB), Similarity * 100 */
  val colorKey: Pair<String, Int>?,
  /** Target output container format: [OutputFormat.GIF] or [OutputFormat.ANIMATED_WEBP]. */
  val outputFormat: OutputFormat = OutputFormat.GIF,
  /** null when outputFormat == GIF; MEDIUM by default when outputFormat == ANIMATED_WEBP */
  val webpQuality: WebpQuality? = null,
  /** Color filter preset applied during palette generation and final encoding. Defaults to
   *  [ExportColorFilter.NONE] (no filter). */
  val colorFilter: ExportColorFilter = ExportColorFilter.NONE,
) : Serializable {

  init {
    TextRender.render(textRender, videoWH.first, videoWH.second).saveToPng(ADD_TEXT_RENDER_PNG_PATH)
  }

  /**
   * Returns a lightweight [TaskBuilderVideoToGifForPreview] containing only the parameters needed
   * by the in-app preview renderer, avoiding the overhead of a full task object.
   */
  fun getForPreviewOnly() = TaskBuilderVideoToGifForPreview(shortLength, colorQuality, lossy, videoWH, colorKey, colorFilter)

  /**
   * Returns the estimated number of output frames computed from the trimmed duration, [outputFps],
   * and [outputSpeed]. Used to track encoding progress.
   */
  fun getOutputFramesEstimated() = ceil((trimTime?.let { it.second - it.first } ?: duration) * outputFps / outputSpeed / 1000.0).toInt()

  /**
   * Builds the FFmpeg command that extracts frames from the source video into individual BMP files,
   * applying speed, frame rate, crop, resolution, loop mode, text overlay, and colour-key filters.
   */
  fun getCommandExtractFrame(): String = buildExtractFrameCommand(
    inputVideoPath = inputVideoPath,
    textRenderPath = ADD_TEXT_RENDER_PNG_PATH,
    trimTime = trimTime,
    outputSpeed = outputSpeed,
    outputFps = outputFps,
    cropParams = cropParams,
    shortLength = shortLength,
    colorKey = colorKey,
    loopMode = loopMode,
    outputFramesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
  )

  /**
   * Builds the FFmpeg command that analyses the extracted BMP frames and generates an optimised
   * GIF palette file at [MyConstants.PALETTE_PATH].
   */
  fun getCommandCreatePalette(): String = buildPaletteCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    colorQuality = colorQuality,
    palettePath = MyConstants.PALETTE_PATH,
    colorFilter = colorFilter,
  )

  /**
   * Builds the FFmpeg command that assembles the extracted BMP frames into a final GIF file using
   * the palette generated by [getCommandCreatePalette].
   */
  fun getCommandVideoToGif(): String = buildGifCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    palettePath = MyConstants.PALETTE_PATH,
    outputPath = OUTPUT_GIF_TEMP_PATH,
    fps = outputFps,
    finalDelay = finalDelay,
    colorFilter = colorFilter,
  )

  /**
   * Builds the FFmpeg command that assembles the extracted BMP frames into a final Animated WebP
   * file. Requires [webpQuality] to be non-null; throws [IllegalStateException] otherwise.
   */
  fun getCommandVideoToWebp(): String = buildWebpCommand(
    framesPath = MyConstants.VIDEO_TO_GIF_EXTRACTED_FRAMES_PATH,
    outputPath = OUTPUT_WEBP_TEMP_PATH,
    fps = outputFps,
    quality = checkNotNull(webpQuality) { "webpQuality must be set when outputFormat == ANIMATED_WEBP" },
    vfChain = colorFilter.vfChain,
  )

  companion object {
    /**
     * Computes the FFmpeg `-vf scale=` segment that constrains the output to [shortLength] pixels
     * on the short side. Returns an empty string when [shortLength] is 0 or already at or above
     * the source resolution (no downscaling needed).
     */
    internal fun resolutionParams(cropParams: CropParams, shortLength: Int): String {
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

    /**
     * Pure function that builds the frame-extraction FFmpeg command. Extracted from the instance
     * method so it can be unit-tested without constructing a [TaskBuilderVideoToGif], which would
     * trigger the `init` block's file I/O.
     */
    internal fun buildExtractFrameCommand(
      inputVideoPath: String,
      textRenderPath: String,
      trimTime: Pair<Int, Int>?,
      outputSpeed: Float,
      outputFps: Int,
      cropParams: CropParams,
      shortLength: Int,
      colorKey: Pair<String, Int>?,
      loopMode: ExportLoopMode,
      outputFramesPath: String,
    ): String {
      val trimArg = trimTime?.let { "-ss ${it.first}ms -to ${it.second}ms " } ?: ""
      val colorKeyArg = colorKey?.let { ",colorkey=#${it.first}:${it.second / 100f}:0" } ?: ""
      val baseChain = "[0vPreprocessed][1:v] overlay=0:0," +
        cropParams.toFFmpegCropCommand() + resolutionParams(cropParams, shortLength) + colorKeyArg
      val (filterTail, mapArg) = when (loopMode) {
        ExportLoopMode.FORWARD -> "" to ""
        ExportLoopMode.REVERSE -> ",reverse" to ""
        ExportLoopMode.BOOMERANG ->
          "[base]; [base]split[v1][v2]; [v2]reverse[v2r]; [v1][v2r]concat=n=2:v=1:a=0[out]" to "-map \"[out]\" "
      }
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN $trimArg-i \"$inputVideoPath\" -i \"$textRenderPath\" " +
        "-filter_complex \"[0:v] setpts=PTS/$outputSpeed,fps=fps=$outputFps [0vPreprocessed]; $baseChain$filterTail\" " +
        "${mapArg}\"${outputFramesPath}%06d.bmp\""
    }

    /**
     * Pure function that builds the palette-generation FFmpeg command. Extracted from the instance
     * method so it can be unit-tested without constructing a [TaskBuilderVideoToGif].
     */
    internal fun buildPaletteCommand(framesPath: String, colorQuality: Int, palettePath: String, colorFilter: ExportColorFilter): String {
      val vfPrefix = colorFilter.vfChain?.let { "$it," } ?: ""
      val maxColors = if (colorFilter == ExportColorFilter.VINTAGE) 64 else colorQuality
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -i \"${framesPath}%06d.bmp\" " +
        "-vf ${vfPrefix}palettegen=max_colors=${maxColors}:stats_mode=diff -y \"$palettePath\""
    }

    /**
     * Pure function that builds the GIF-encoding FFmpeg command. Extracted from the instance
     * method so it can be unit-tested without constructing a [TaskBuilderVideoToGif].
     */
    internal fun buildGifCommand(framesPath: String, palettePath: String, outputPath: String, fps: Int, finalDelay: Int, colorFilter: ExportColorFilter): String {
      val filterComplex = colorFilter.vfChain?.let {
        "\"[0:v]$it[v];[v][1:v]paletteuse=dither=bayer\""
      } ?: "paletteuse=dither=bayer"
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -framerate $fps " +
        "-i \"${framesPath}%06d.bmp\" -i \"$palettePath\" " +
        "-filter_complex $filterComplex -final_delay $finalDelay -y \"$outputPath\""
    }

    /**
     * Pure function that builds the Animated WebP encoding FFmpeg command. Extracted from the
     * instance method so it can be unit-tested without constructing a [TaskBuilderVideoToGif].
     */
    internal fun buildWebpCommand(framesPath: String, outputPath: String, fps: Int, quality: WebpQuality, vfChain: String? = null): String {
      val qualityFlags = if (quality.lossless) "-lossless 1" else "-quality ${quality.ffmpegQuality}"
      val vfArg = vfChain?.let { "-vf \"$it\" " } ?: ""
      return "$FFMPEG_COMMAND_PREFIX_FOR_ALL_AN -framerate $fps " +
        "-i \"${framesPath}%06d.bmp\" ${vfArg}$qualityFlags -compression_level 6 -loop 0 -y \"$outputPath\""
    }
  }
}
