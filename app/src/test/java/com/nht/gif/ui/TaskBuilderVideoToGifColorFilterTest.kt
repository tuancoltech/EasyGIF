package com.nht.gif

import com.nht.gif.model.ExportColorFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBuilderVideoToGifColorFilterTest {

  private val framesPath = "/cache/frames/"
  private val palettePath = "/cache/palette.png"
  private val outputPath = "/cache/output.gif"
  private val fps = 10
  private val colorQuality = 256
  private val finalDelay = 0

  private fun paletteCmd(filter: ExportColorFilter) =
    TaskBuilderVideoToGif.buildPaletteCommand(framesPath, colorQuality, palettePath, filter)

  private fun gifCmd(filter: ExportColorFilter) =
    TaskBuilderVideoToGif.buildGifCommand(framesPath, palettePath, outputPath, fps, finalDelay, filter)

  // T3.6
  @Test
  fun `buildPaletteCommand with NONE contains palettegen with no filter prefix and uses colorQuality`() {
    val cmd = paletteCmd(ExportColorFilter.NONE)
    assertTrue(cmd.contains("-vf palettegen=max_colors=$colorQuality:stats_mode=diff"))
    assertFalse(cmd.contains("curves="))
    assertFalse(cmd.contains("hue="))
  }

  // T3.7
  @Test
  fun `buildPaletteCommand with VINTAGE contains curves-hue chain and palettegen=max_colors=64`() {
    val cmd = paletteCmd(ExportColorFilter.VINTAGE)
    assertTrue(cmd.contains(ExportColorFilter.VINTAGE.vfChain!!))
    assertTrue(cmd.contains("palettegen=max_colors=64"))
    assertFalse(cmd.contains("max_colors=$colorQuality"))
  }

  // T3.8
  @Test
  fun `buildPaletteCommand with NEON contains hue and eq chain immediately before palettegen`() {
    val cmd = paletteCmd(ExportColorFilter.NEON)
    assertTrue(cmd.contains("${ExportColorFilter.NEON.vfChain},palettegen"))
  }

  // T3.9
  @Test
  fun `buildPaletteCommand with NOIR contains hue and eq chain immediately before palettegen`() {
    val cmd = paletteCmd(ExportColorFilter.NOIR)
    assertTrue(cmd.contains("${ExportColorFilter.NOIR.vfChain},palettegen"))
  }

  // T3.10
  @Test
  fun `buildGifCommand with VINTAGE filtergraph contains curves-hue chain before paletteuse`() {
    val cmd = gifCmd(ExportColorFilter.VINTAGE)
    val vintageChain = ExportColorFilter.VINTAGE.vfChain!!
    val chainIndex = cmd.indexOf(vintageChain)
    val paletteuseIndex = cmd.indexOf("paletteuse=dither=bayer")
    assertTrue("vfChain not found in command", chainIndex >= 0)
    assertTrue("paletteuse must appear after vfChain", paletteuseIndex > chainIndex)
  }

  @Test
  fun `buildGifCommand with NONE uses simple paletteuse without filter graph`() {
    val cmd = gifCmd(ExportColorFilter.NONE)
    assertTrue(cmd.contains("paletteuse=dither=bayer"))
    assertFalse(cmd.contains("[0:v]"))
  }
}
