package com.nht.gif

import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.WebpQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBuilderVideoToGifWebpCommandTest {

  private val framesPath = "/cache/frames/"
  private val outputPath = "/cache/output_temp.webp"
  private val fps = 10

  private fun cmd(quality: WebpQuality) =
    TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, fps, quality)

  @Test
  fun `SMALL preset contains -quality 50 -compression_level 6`() {
    val command = cmd(WebpQuality.SMALL)
    assertTrue(command.contains("-quality 50 -compression_level 6"))
  }

  @Test
  fun `MEDIUM preset contains -quality 75 -compression_level 6`() {
    val command = cmd(WebpQuality.MEDIUM)
    assertTrue(command.contains("-quality 75 -compression_level 6"))
  }

  @Test
  fun `HIGH preset contains -quality 90 -compression_level 6`() {
    val command = cmd(WebpQuality.HIGH)
    assertTrue(command.contains("-quality 90 -compression_level 6"))
  }

  @Test
  fun `LOSSLESS preset contains -lossless 1 -compression_level 6 and no -quality flag`() {
    val command = cmd(WebpQuality.LOSSLESS)
    assertTrue(command.contains("-lossless 1 -compression_level 6"))
    assertFalse(command.contains("-quality"))
  }

  @Test
  fun `command ends with -loop 0 -y followed by output path`() {
    val command = cmd(WebpQuality.MEDIUM)
    assertTrue(command.trimEnd().endsWith("-loop 0 -y \"$outputPath\""))
  }

  // T3.13
  @Test
  fun `VINTAGE filter contains color tone chain but no max_colors or dither`() {
    val command = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, fps, WebpQuality.MEDIUM, ExportColorFilter.VINTAGE.vfChain)
    assertTrue(command.contains(ExportColorFilter.VINTAGE.vfChain!!))
    assertFalse(command.contains("max_colors"))
    assertFalse(command.contains("--dither"))
  }

  // T3.14
  @Test
  fun `NEON filter contains hue and eq chain`() {
    val command = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, fps, WebpQuality.MEDIUM, ExportColorFilter.NEON.vfChain)
    assertTrue(command.contains(ExportColorFilter.NEON.vfChain!!))
  }

  // T3.15
  @Test
  fun `NONE filter produces no -vf argument`() {
    val command = TaskBuilderVideoToGif.buildWebpCommand(framesPath, outputPath, fps, WebpQuality.MEDIUM, null)
    assertFalse(command.contains("-vf"))
  }
}
