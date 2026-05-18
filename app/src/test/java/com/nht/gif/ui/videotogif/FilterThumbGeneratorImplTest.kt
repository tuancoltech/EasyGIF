package com.nht.gif.ui.videotogif

import com.nht.gif.model.ExportColorFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FilterThumbGeneratorImplTest {

  private val testDispatcher = StandardTestDispatcher()

  private fun makeGenerator(
    ffmpegExecutor: (String) -> Boolean = { false },
    tempBaseDir: String = System.getProperty("java.io.tmpdir")!!,
  ) = FilterThumbGeneratorImpl(
    inputVideoPath = "/data/test.mp4",
    clipDurationMs = 4000L,
    tempBaseDir = tempBaseDir,
    ioDispatcher = testDispatcher,
    ffmpegExecutor = ffmpegExecutor,
    bitmapDecoder = { null },
  )

  // T2.9
  @Test
  fun `buildApplyFilterCommand contains correct vfChain crop and scale for each non-None preset`() {
    val generator = makeGenerator()
    ExportColorFilter.entries.filter { it != ExportColorFilter.NONE }.forEach { filter ->
      val cmd = generator.buildApplyFilterCommand("/base.png", filter, "/out.jpg")
      assertTrue("${filter.name}: vfChain absent", cmd.contains(filter.vfChain!!))
      assertTrue("${filter.name}: crop filter absent", cmd.contains("crop=min(iw\\,ih)"))
      assertTrue("${filter.name}: scale=72:72 absent", cmd.contains("scale=72:72"))
    }
  }

  // T2.10
  @Test
  fun `NONE preset skips the FFmpeg filter step`() = runTest(testDispatcher) {
    var callCount = 0
    val generator = makeGenerator(ffmpegExecutor = { callCount++; false })
    generator.generate { _, _ -> }
    // 1 (base frame extraction) + 3 (VINTAGE, NEON, NOIR) = 4; NONE adds no call
    assertEquals(4, callCount)
  }

  // T2.11
  @Test
  fun `failure for one preset does not cancel other preset coroutines`() = runTest(testDispatcher) {
    val emitted = mutableSetOf<ExportColorFilter>()
    val generator = makeGenerator(
      ffmpegExecutor = { cmd ->
        // Fail only VINTAGE; succeed for everything else
        !cmd.contains(ExportColorFilter.VINTAGE.vfChain!!)
      }
    )
    generator.generate { filter, _ -> emitted.add(filter) }
    assertEquals("all 4 presets must emit a result", ExportColorFilter.entries.size, emitted.size)
    assertTrue("VINTAGE must still emit (with null bitmap)", emitted.contains(ExportColorFilter.VINTAGE))
  }

  // T2.12
  @Test
  fun `temp files are deleted after generation completes`() = runTest(testDispatcher) {
    val baseDir = Files.createTempDirectory("t212").toFile()
    try {
      makeGenerator(tempBaseDir = baseDir.absolutePath).generate { _, _ -> }
      assertFalse("temp dir must be gone after completion", File(baseDir, "filter_thumbs/0").exists())
    } finally {
      baseDir.deleteRecursively()
    }
  }

  // T2.13
  @Test
  fun `temp files are deleted when generation is cancelled mid-extraction`() {
    val baseDir = Files.createTempDirectory("t213").toFile()
    val started = CountDownLatch(1)
    val unblock = CountDownLatch(1)
    try {
      val generator = FilterThumbGeneratorImpl(
        inputVideoPath = "/data/test.mp4",
        clipDurationMs = 4000L,
        tempBaseDir = baseDir.absolutePath,
        ioDispatcher = Dispatchers.IO,
        ffmpegExecutor = {
          started.countDown()  // temp dir already created at this point
          unblock.await()      // block until we're ready to let it proceed
          false
        },
        bitmapDecoder = { null },
      )
      val scope = CoroutineScope(Dispatchers.IO + Job())
      val job = scope.launch { generator.generate { _, _ -> } }
      started.await(5, TimeUnit.SECONDS)  // wait until extraction is running and temp dir exists
      job.cancel()
      unblock.countDown()                 // let ffmpegExecutor return so cancellation can propagate
      runBlocking { job.join() }
      assertFalse("temp dir must be gone after cancellation", File(baseDir, "filter_thumbs/0").exists())
    } finally {
      baseDir.deleteRecursively()
    }
  }
}
