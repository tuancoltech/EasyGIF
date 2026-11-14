package com.nht.gif.ui.videotogif

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.nht.gif.CropParams
import com.nht.gif.MyApplication
import com.nht.gif.TaskBuilderVideoToGifForPreview
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files

class PreviewControllerTest {

  private val frame: Bitmap = mockk(relaxed = true)
  private val decoded: Bitmap = mockk(relaxed = true)
  private val cropParams = CropParams(outW = 200, outH = 100, x = 0, y = 0)
  private val task = TaskBuilderVideoToGifForPreview(
    shortLength = 240, colorQuality = 256, lossy = null, videoWH = 200 to 100, colorKey = null,
  )

  /** Decode result per test; null simulates a missing/unreadable stage output. */
  private var decodeResult: Bitmap? = null

  @Before
  fun setUp() {
    mockkStatic(Bitmap::class, BitmapFactory::class, FFmpegKit::class, Log::class)
    every { Log.e(any(), any()) } returns 0 // gifsicleLossy logs its failure when the binary is absent
    every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns frame
    every { FFmpegKit.execute(any()) } returns mockk(relaxed = true)
    every { BitmapFactory.decodeFile(any()) } answers { decodeResult }
  }

  @After
  fun tearDown() = unmockkAll()

  @Test
  fun `render returns null instead of throwing when the decoded output is missing`() = runBlocking {
    decodeResult = null
    assertNull(PreviewController(frame, cropParams).render(task))
  }

  @Test
  fun `render returns null when only the lossy output is missing`() = runBlocking {
    every { BitmapFactory.decodeFile(any()) } answers { decodeResult.takeUnless { arg<String>(0).endsWith(".50.gif") } }
    decodeResult = decoded
    assertNull(PreviewController(frame, cropParams).render(task.copy(lossy = 50)))
  }

  @Test
  fun `render after a failed render re-runs every stage`() = runBlocking {
    val controller = PreviewController(frame, cropParams)
    decodeResult = null
    assertNull(controller.render(task))

    decodeResult = decoded
    assertSame(decoded, controller.render(task))
    // palettegen + paletteuse must run again; a poisoned stage cache would skip them
    verify(exactly = FFMPEG_CALLS_PER_RENDER * 2) { FFmpegKit.execute(any()) }
  }

  @Test
  fun `successful render is cached and does not re-run stages`() = runBlocking {
    decodeResult = decoded
    val controller = PreviewController(frame, cropParams)
    assertSame(decoded, controller.render(task))
    assertSame(decoded, controller.render(task))
    verify(exactly = FFMPEG_CALLS_PER_RENDER) { FFmpegKit.execute(any()) }
  }

  companion object {
    /** palettegen + paletteuse; scale/colorkey/filter stages run no FFmpeg for this task. */
    private const val FFMPEG_CALLS_PER_RENDER = 2

    @JvmStatic
    @BeforeClass
    fun initAppContext() {
      val appInfo = mockk<ApplicationInfo>(relaxed = true).apply { nativeLibraryDir = "/native" }
      MyApplication.appContext = mockk<Context>(relaxed = true) {
        every { cacheDir } returns Files.createTempDirectory("preview_test").toFile()
        every { applicationInfo } returns appInfo
      }
    }
  }
}
