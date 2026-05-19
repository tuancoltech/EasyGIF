package com.nht.gif

import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.WebpQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBuilderVideoToGifExtractFrameCommandTest {

  private val inputVideoPath = "/sdcard/input.mp4"
  private val textRenderPath = "/cache/text_render.png"
  private val framesPath = "/cache/frames/"
  private val cropParams = CropParams(outW = 1280, outH = 720, x = 0, y = 0)
  private val shortLength = 0
  private val outputSpeed = 1.0f
  private val outputFps = 15

  private fun cmd(
    loopMode: ExportLoopMode,
    trimTime: Pair<Int, Int>? = null,
    colorKey: Pair<String, Int>? = null,
  ) = TaskBuilderVideoToGif.buildExtractFrameCommand(
    inputVideoPath = inputVideoPath,
    textRenderPath = textRenderPath,
    trimTime = trimTime,
    outputSpeed = outputSpeed,
    outputFps = outputFps,
    cropParams = cropParams,
    shortLength = shortLength,
    colorKey = colorKey,
    loopMode = loopMode,
    outputFramesPath = framesPath,
  )

  // T2.3 — regression guard: FORWARD must be byte-identical to former reverse=false output
  @Test
  fun `FORWARD output matches former reverse=false output`() {
    val forward = cmd(ExportLoopMode.FORWARD)
    val legacy = TaskBuilderVideoToGif.buildExtractFrameCommand(
      inputVideoPath = inputVideoPath,
      textRenderPath = textRenderPath,
      trimTime = null,
      outputSpeed = outputSpeed,
      outputFps = outputFps,
      cropParams = cropParams,
      shortLength = shortLength,
      colorKey = null,
      loopMode = ExportLoopMode.FORWARD,
      outputFramesPath = framesPath,
    )
    assertEquals(legacy, forward)
    assertFalse("must not contain ,reverse", forward.contains(",reverse"))
    assertFalse("must not contain concat", forward.contains("concat"))
    assertFalse("must not contain -map", forward.contains("-map"))
  }

  // T2.4 — FORWARD: no reverse filter, no concat filter
  @Test
  fun `FORWARD contains no reverse and no concat filter`() {
    val command = cmd(ExportLoopMode.FORWARD)
    assertFalse(command.contains(",reverse"))
    assertFalse(command.contains("concat"))
    assertFalse(command.contains("-map"))
  }

  // T2.5 — REVERSE: contains ,reverse in filter chain
  @Test
  fun `REVERSE contains ,reverse in the filter chain`() {
    val command = cmd(ExportLoopMode.REVERSE)
    assertTrue(command.contains(",reverse"))
    assertFalse(command.contains("concat"))
    assertFalse(command.contains("-map"))
  }

  // T2.6 — BOOMERANG: contains split→reverse→concat segment and -map [out]
  @Test
  fun `BOOMERANG contains split-reverse-concat segment and maps out`() {
    val command = cmd(ExportLoopMode.BOOMERANG)
    assertTrue(command.contains("[base]split[v1][v2]"))
    assertTrue(command.contains("[v2]reverse[v2r]"))
    assertTrue(command.contains("[v1][v2r]concat=n=2:v=1:a=0[out]"))
    assertTrue(command.contains("-map \"[out]\""))
    assertFalse("BOOMERANG must not emit standalone ,reverse", command.contains(",reverse"))
  }

  // T2.7 — buildPaletteCommand identical across all loop modes
  @Test
  fun `buildPaletteCommand is identical across all loop modes`() {
    val palette = "/cache/palette.png"
    val colorQuality = 256
    val forward = TaskBuilderVideoToGif.buildPaletteCommand(framesPath, colorQuality, palette, ExportColorFilter.NONE)
    val reverse = TaskBuilderVideoToGif.buildPaletteCommand(framesPath, colorQuality, palette, ExportColorFilter.NONE)
    val boomerang = TaskBuilderVideoToGif.buildPaletteCommand(framesPath, colorQuality, palette, ExportColorFilter.NONE)
    assertEquals(forward, reverse)
    assertEquals(forward, boomerang)
  }

  // T2.8 — buildGifCommand identical across all loop modes
  @Test
  fun `buildGifCommand is identical across all loop modes`() {
    val palettePath = "/cache/palette.png"
    val outputPath = "/cache/output.gif"
    val finalDelay = 0
    val forward = TaskBuilderVideoToGif.buildGifCommand(framesPath, palettePath, outputPath, outputFps, finalDelay, ExportColorFilter.NONE)
    val reverse = TaskBuilderVideoToGif.buildGifCommand(framesPath, palettePath, outputPath, outputFps, finalDelay, ExportColorFilter.NONE)
    val boomerang = TaskBuilderVideoToGif.buildGifCommand(framesPath, palettePath, outputPath, outputFps, finalDelay, ExportColorFilter.NONE)
    assertEquals(forward, reverse)
    assertEquals(forward, boomerang)
  }

  // T2.9 — buildWebpCommand identical across all loop modes
  @Test
  fun `buildWebpCommand is identical across all loop modes`() {
    val outputPath = "/cache/output.webp"
    val forward = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, outputFps, WebpQuality.MEDIUM)
    val reverse = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, outputFps, WebpQuality.MEDIUM)
    val boomerang = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, outputFps, WebpQuality.MEDIUM)
    assertEquals(forward, reverse)
    assertEquals(forward, boomerang)
  }
}
