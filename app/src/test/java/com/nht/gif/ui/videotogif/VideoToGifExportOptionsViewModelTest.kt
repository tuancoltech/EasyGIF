package com.nht.gif.ui.videotogif

import com.nht.gif.CropParams
import com.nht.gif.data.EstimationSettings
import com.nht.gif.data.FileSizeEstimator
import com.nht.gif.ui.videotogif.FilterThumbGenerator
import com.nht.gif.model.EstimationState
import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.WebpQuality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoToGifExportOptionsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val mockEstimator: FileSizeEstimator = mockk(relaxed = true)
  private val mockThumbGenerator: FilterThumbGenerator = mockk(relaxed = true)
  private val testCropParams = CropParams(640, 480, 0, 0)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
    estimator: FileSizeEstimator = mockEstimator,
    thumbGenerator: FilterThumbGenerator = mockThumbGenerator,
  ) = VideoToGifExportOptionsViewModel(
    inputVideoPath = "/data/test.mp4",
    duration = 5000,
    cropParams = testCropParams,
    outputSpeed = 1f,
    estimator = estimator,
    thumbGenerator = thumbGenerator,
  )

  // T1.8
  @Test
  fun `outputFormat defaults to GIF on creation`() {
    val viewModel = createViewModel()
    assertEquals(OutputFormat.GIF, viewModel.outputFormat.value)
  }

  // T1.9
  @Test
  fun `setting outputFormat to ANIMATED_WEBP updates state`() {
    val viewModel = createViewModel()
    viewModel.setOutputFormat(OutputFormat.ANIMATED_WEBP)
    assertEquals(OutputFormat.ANIMATED_WEBP, viewModel.outputFormat.value)
  }

  // T1.10
  @Test
  fun `setting outputFormat back to GIF updates state`() {
    val viewModel = createViewModel()
    viewModel.setOutputFormat(OutputFormat.ANIMATED_WEBP)
    viewModel.setOutputFormat(OutputFormat.GIF)
    assertEquals(OutputFormat.GIF, viewModel.outputFormat.value)
  }

  // T1.11
  @Test
  fun `colorFilter defaults to NONE on creation`() {
    val viewModel = createViewModel()
    assertEquals(ExportColorFilter.NONE, viewModel.colorFilter.value)
  }

  // T1.14
  @Test
  fun `setColorFilter updates colorFilter state for each preset`() {
    val viewModel = createViewModel()
    ExportColorFilter.entries.forEach { filter ->
      viewModel.setColorFilter(filter)
      assertEquals(filter, viewModel.colorFilter.value)
    }
  }

  // T2.13
  @Test
  fun `webpQuality defaults to MEDIUM when outputFormat switches to ANIMATED_WEBP`() {
    val viewModel = createViewModel()
    viewModel.setOutputFormat(OutputFormat.ANIMATED_WEBP)
    assertEquals(WebpQuality.MEDIUM, viewModel.webpQuality.value)
  }

  // T2.14
  @Test
  fun `showLosslessWarning is true only when webpQuality is LOSSLESS`() {
    val viewModel = createViewModel()
    viewModel.setOutputFormat(OutputFormat.ANIMATED_WEBP)

    viewModel.setWebpQuality(WebpQuality.SMALL)
    assertFalse(viewModel.showLosslessWarning.value)

    viewModel.setWebpQuality(WebpQuality.MEDIUM)
    assertFalse(viewModel.showLosslessWarning.value)

    viewModel.setWebpQuality(WebpQuality.HIGH)
    assertFalse(viewModel.showLosslessWarning.value)

    viewModel.setWebpQuality(WebpQuality.LOSSLESS)
    assertTrue(viewModel.showLosslessWarning.value)
  }

  // T3.17
  @Test
  fun `estimationState transitions to Ready on successful estimation`() = runTest {
    val estimator = mockk<FileSizeEstimator>()
    coEvery { estimator.estimate(any()) } returns (100_000L to 80_000L)

    val viewModel = createViewModel(estimator)
    assertEquals(EstimationState.Loading, viewModel.estimationState.value)

    advanceUntilIdle()
    assertEquals(EstimationState.Ready(100_000L, 80_000L), viewModel.estimationState.value)
  }

  // T3.18
  @Test
  fun `estimationState transitions to Error when estimator throws`() = runTest {
    val estimator = mockk<FileSizeEstimator>()
    coEvery { estimator.estimate(any()) } throws RuntimeException("FFmpeg error")

    val viewModel = createViewModel(estimator)
    advanceUntilIdle()
    assertEquals(EstimationState.Error, viewModel.estimationState.value)
  }

  // T3.19
  @Test
  fun `setting change within 300ms debounce cancels pending estimation`() = runTest {
    var callCount = 0
    val estimator = mockk<FileSizeEstimator>()
    coEvery { estimator.estimate(any()) } coAnswers { callCount++; 1L to 2L }

    val viewModel = createViewModel(estimator)

    // Let initial combine emit and start the 300ms debounce, but don't fire it yet
    advanceTimeBy(100)

    // Change fps within the 300ms window → cancels the pending job, resets debounce
    viewModel.setFps(5)

    // Let everything settle (new 300ms debounce + estimation)
    advanceUntilIdle()

    // Only 1 estimation ran; the first (cancelled) job never reached estimate()
    assertEquals(1, callCount)
    assertTrue(viewModel.estimationState.value is EstimationState.Ready)
  }

  // T3.20 — regression for rapid quality switch producing ~0 KB WebP estimate.
  // Root cause: quality change after debounce fires (while estimation is in flight) must
  // cancel the in-flight job and re-run with the new settings.
  @Test
  fun `quality switch while estimation is in flight re-runs with updated settings`() = runTest {
    var callCount = 0
    var lastSettings: EstimationSettings? = null
    val estimator = mockk<FileSizeEstimator>()
    coEvery { estimator.estimate(any()) } coAnswers {
      callCount++
      lastSettings = firstArg()
      if (callCount == 1) {
        delay(500) // simulate slow in-flight estimation (e.g. FFmpeg running)
        1L to 2L
      } else {
        10L to 20L
      }
    }

    val viewModel = createViewModel(estimator)

    // First debounce fires — estimation 1 starts (blocked on delay above)
    advanceTimeBy(301)

    // Switch quality while estimation 1 is still in progress
    viewModel.setWebpQuality(WebpQuality.SMALL)

    // Settle: estimation 1 is cancelled, debounce 2 fires, estimation 2 completes
    advanceUntilIdle()

    assertEquals(2, callCount)
    assertEquals(WebpQuality.SMALL, lastSettings?.webpQuality)
    assertEquals(EstimationState.Ready(10L, 20L), viewModel.estimationState.value)
  }

  // T1.15
  @Test
  fun `loopMode defaults to FORWARD on creation`() {
    val viewModel = createViewModel()
    assertEquals(ExportLoopMode.FORWARD, viewModel.loopMode.value)
  }

  // T1.16
  @Test
  fun `smartTrimEnabled defaults to false on creation`() {
    val viewModel = createViewModel()
    assertFalse(viewModel.smartTrimEnabled.value)
  }

  // T1.17
  @Test
  fun `setLoopMode updates loopMode state for each mode`() {
    val viewModel = createViewModel()
    ExportLoopMode.entries.forEach { mode ->
      viewModel.setLoopMode(mode)
      assertEquals(mode, viewModel.loopMode.value)
    }
  }

  // T1.18 — boomerang size warning is visible only when loopMode == BOOMERANG
  @Test
  fun `boomerang warning visible only for BOOMERANG mode`() {
    val viewModel = createViewModel()
    val shouldShowWarning = { viewModel.loopMode.value == ExportLoopMode.BOOMERANG }

    viewModel.setLoopMode(ExportLoopMode.FORWARD)
    assertFalse(shouldShowWarning())

    viewModel.setLoopMode(ExportLoopMode.REVERSE)
    assertFalse(shouldShowWarning())

    viewModel.setLoopMode(ExportLoopMode.BOOMERANG)
    assertTrue(shouldShowWarning())
  }

  // T1.19 — smart trim row visible for REVERSE and BOOMERANG, hidden for FORWARD
  @Test
  fun `smart trim row visible for REVERSE and BOOMERANG, hidden for FORWARD`() {
    val viewModel = createViewModel()
    val shouldShowSmartTrim = {
      viewModel.loopMode.value == ExportLoopMode.REVERSE ||
        viewModel.loopMode.value == ExportLoopMode.BOOMERANG
    }

    viewModel.setLoopMode(ExportLoopMode.FORWARD)
    assertFalse(shouldShowSmartTrim())

    viewModel.setLoopMode(ExportLoopMode.REVERSE)
    assertTrue(shouldShowSmartTrim())

    viewModel.setLoopMode(ExportLoopMode.BOOMERANG)
    assertTrue(shouldShowSmartTrim())
  }

  // T2.14
  @Test
  fun `loadThumbnails is idempotent — generate is called exactly once even when invoked twice`() = runTest {
    val generator = mockk<FilterThumbGenerator>(relaxed = true)
    val viewModel = createViewModel(thumbGenerator = generator)
    viewModel.loadThumbnails()
    viewModel.loadThumbnails()
    advanceUntilIdle()
    coVerify(exactly = 1) { generator.generate(any()) }
  }
}
