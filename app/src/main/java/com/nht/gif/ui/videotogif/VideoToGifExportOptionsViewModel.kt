package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nht.gif.CropParams
import com.nht.gif.MyConstants
import com.nht.gif.data.EstimationSettings
import com.nht.gif.data.FileSizeEstimator
import com.nht.gif.data.FileSizeEstimatorImpl
import com.nht.gif.model.EstimationState
import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.WebpQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds UI state for the export-options dialog (format selection, quality presets, size estimates). */
class VideoToGifExportOptionsViewModel(
  private val inputVideoPath: String,
  private val duration: Int,
  private val cropParams: CropParams,
  private val outputSpeed: Float,
  private val estimator: FileSizeEstimator,
  private val thumbGenerator: FilterThumbGenerator,
  private val smartTrimDetectorFactory: (startMs: Long, endMs: Long) -> SmartTrimDetector,
) : ViewModel() {

  /**
   * One-shot events that drive the Save / Smart Trim flow.
   * The Fragment collects this flow to decide whether to proceed or show the confirmation dialog.
   */
  sealed class SmartTrimEvent {
    /**
     * Proceed to export. [overrideEndMs] is non-null when Smart Trim detected a better end point
     * and the user accepted it; null means use the current UI trim state unchanged.
     */
    data class Proceed(val overrideEndMs: Long?) : SmartTrimEvent()

    /**
     * Smart Trim found a candidate end point. The Fragment must show the confirmation dialog
     * and call [onSmartTrimDialogResult] with the user's choice.
     */
    data class ConfirmSmartTrim(val originalEndMs: Long, val detectedEndMs: Long) : SmartTrimEvent()
  }

  private val _outputFormat = MutableStateFlow(OutputFormat.GIF)
  val outputFormat: StateFlow<OutputFormat> = _outputFormat.asStateFlow()

  private val _webpQuality = MutableStateFlow(WebpQuality.MEDIUM)
  val webpQuality: StateFlow<WebpQuality> = _webpQuality.asStateFlow()

  private val _showLosslessWarning = MutableStateFlow(false)
  val showLosslessWarning: StateFlow<Boolean> = _showLosslessWarning.asStateFlow()

  private val _fps = MutableStateFlow(10)
  val fps: StateFlow<Int> = _fps.asStateFlow()

  private val _shortLength = MutableStateFlow(240)
  val shortLength: StateFlow<Int> = _shortLength.asStateFlow()

  // Defaults match the UI initial selections: Color Quality = High (128 colors), Clarity = High (lossy 30)
  private val _colorQuality = MutableStateFlow(128)
  val colorQuality: StateFlow<Int> = _colorQuality.asStateFlow()

  private val _lossy: MutableStateFlow<Int?> = MutableStateFlow(30)
  val lossy: StateFlow<Int?> = _lossy.asStateFlow()

  private val _colorFilter = MutableStateFlow(ExportColorFilter.NONE)
  val colorFilter: StateFlow<ExportColorFilter> = _colorFilter.asStateFlow()

  private val _loopMode = MutableStateFlow(ExportLoopMode.FORWARD)
  val loopMode: StateFlow<ExportLoopMode> = _loopMode.asStateFlow()

  private val _smartTrimEnabled = MutableStateFlow(false)
  val smartTrimEnabled: StateFlow<Boolean> = _smartTrimEnabled.asStateFlow()

  private val _filterThumbnails = MutableStateFlow<Map<ExportColorFilter, Result<Bitmap>?>>(
    ExportColorFilter.entries.associateWith { null }
  )
  val filterThumbnails: StateFlow<Map<ExportColorFilter, Result<Bitmap>?>> = _filterThumbnails.asStateFlow()

  private val _estimationState = MutableStateFlow<EstimationState>(EstimationState.Loading)
  val estimationState: StateFlow<EstimationState> = _estimationState.asStateFlow()

  private val _isDetecting = MutableStateFlow(false)

  /** True while Smart Trim detection is running; drives the Save button loading state in the UI. */
  val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

  private val _smartTrimEvent = Channel<SmartTrimEvent>(Channel.BUFFERED)

  /** One-shot events for the Save / Smart Trim flow. Collected by the Fragment. */
  val smartTrimEvent: Flow<SmartTrimEvent> = _smartTrimEvent.receiveAsFlow()

  private var estimationJob: Job? = null
  private var thumbnailJob: Job? = null

  init {
    viewModelScope.launch {
      combine(
        combine(outputFormat, webpQuality, fps, shortLength) { _, _, _, _ -> Unit },
        combine(colorQuality, lossy) { _, _ -> Unit },
      ) { _, _ -> Unit }.collect { scheduleEstimation() }
    }
  }

  fun setOutputFormat(format: OutputFormat) {
    _outputFormat.value = format
    if (format == OutputFormat.ANIMATED_WEBP) setWebpQuality(WebpQuality.MEDIUM)
  }

  fun setWebpQuality(quality: WebpQuality) {
    _webpQuality.value = quality
    _showLosslessWarning.value = quality == WebpQuality.LOSSLESS
  }

  fun setFps(fps: Int) { _fps.value = fps }

  fun setShortLength(shortLength: Int) { _shortLength.value = shortLength }

  fun setColorQuality(colorQuality: Int) { _colorQuality.value = colorQuality }

  fun setLossy(lossy: Int?) { _lossy.value = lossy }

  fun setColorFilter(filter: ExportColorFilter) { _colorFilter.value = filter }

  fun setLoopMode(mode: ExportLoopMode) { _loopMode.value = mode }

  fun setSmartTrimEnabled(enabled: Boolean) { _smartTrimEnabled.value = enabled }

  fun loadThumbnails() {
    if (thumbnailJob != null) return
    thumbnailJob = viewModelScope.launch {
      thumbGenerator.generate { filter, bitmap ->
        val result = if (bitmap != null) Result.success(bitmap) else Result.failure(Exception())
        _filterThumbnails.update { it + (filter to result) }
      }
    }
  }

  private fun scheduleEstimation() {
    estimationJob?.cancel()
    _estimationState.value = EstimationState.Loading
    estimationJob = viewModelScope.launch {
      delay(ESTIMATION_DEBOUNCE_MS)
      _estimationState.value = try {
        val (gif, webp) = estimator.estimate(buildSettings())
        EstimationState.Ready(gif, webp)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        EstimationState.Error
      }
    }
  }

  private fun buildSettings() = EstimationSettings(
    inputVideoPath = inputVideoPath,
    sampleDurationMs = minOf(duration.toLong(), 1000L),
    fullDurationMs = maxOf(duration.toLong(), 1L),
    outputFps = fps.value,
    shortLength = shortLength.value,
    cropParams = cropParams,
    outputSpeed = outputSpeed,
    webpQuality = webpQuality.value,
    colorQuality = colorQuality.value,
    lossy = lossy.value,
  )

  /**
   * Initiates the Save flow. When Smart Trim is enabled and [loopMode] is not FORWARD,
   * runs detection before emitting a [SmartTrimEvent]. Otherwise emits [SmartTrimEvent.Proceed]
   * immediately. Any exception from the detector is silently swallowed (T3.6).
   *
   * @param trimStartMs Start of the user's trim in milliseconds (0 if no trim start is set).
   * @param trimEndMs End of the user's trim in milliseconds, or null if no trim end is set.
   */
  fun requestSave(trimStartMs: Long, trimEndMs: Long?) {
    viewModelScope.launch {
      if (!smartTrimEnabled.value || loopMode.value == ExportLoopMode.FORWARD) {
        _smartTrimEvent.send(SmartTrimEvent.Proceed(overrideEndMs = null))
        return@launch
      }
      val endMs = trimEndMs ?: duration.toLong()
      _isDetecting.value = true
      val detectedMs: Long? = try {
        smartTrimDetectorFactory(trimStartMs, endMs).detect()
      } catch (_: Exception) {
        null
      } finally {
        _isDetecting.value = false
      }
      if (detectedMs != null) {
        _smartTrimEvent.send(SmartTrimEvent.ConfirmSmartTrim(originalEndMs = endMs, detectedEndMs = detectedMs))
      } else {
        _smartTrimEvent.send(SmartTrimEvent.Proceed(overrideEndMs = null))
      }
    }
  }

  /**
   * Responds to the Smart Trim confirmation dialog. Emits [SmartTrimEvent.Proceed] with
   * [detectedEndMs] if the user accepted, or null (keep UI trim state) if the user declined.
   *
   * @param useSmartTrim true if the user tapped "Use Smart Trim"; false for "Use My Trim".
   * @param originalEndMs The user's original trim end time (unused when [useSmartTrim] is false,
   *   kept as a parameter so call sites are explicit about which value they chose).
   * @param detectedEndMs The end time proposed by Smart Trim.
   */
  fun onSmartTrimDialogResult(useSmartTrim: Boolean, originalEndMs: Long, detectedEndMs: Long) {
    _smartTrimEvent.trySend(SmartTrimEvent.Proceed(overrideEndMs = if (useSmartTrim) detectedEndMs else null))
  }

  override fun onCleared() {
    super.onCleared()
    estimationJob?.cancel()
  }

  companion object {
    private const val ESTIMATION_DEBOUNCE_MS = 300L

    fun factory(
      inputVideoPath: String,
      duration: Int,
      cropParams: CropParams,
      outputSpeed: Float,
      estimator: FileSizeEstimator = FileSizeEstimatorImpl(),
      thumbGenerator: FilterThumbGenerator = FilterThumbGeneratorImpl(
        inputVideoPath = inputVideoPath,
        clipDurationMs = duration.toLong(),
        tempBaseDir = MyConstants.CACHE_DIR_PATH,
      ),
      smartTrimDetectorFactory: (Long, Long) -> SmartTrimDetector = { startMs, endMs ->
        SmartTrimDetectorImpl(
          inputVideoPath = inputVideoPath,
          startMs = startMs,
          endMs = endMs,
          tempBaseDir = MyConstants.CACHE_DIR_PATH,
        )
      },
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VideoToGifExportOptionsViewModel(
          inputVideoPath, duration, cropParams, outputSpeed, estimator, thumbGenerator,
          smartTrimDetectorFactory,
        ) as T
    }
  }
}
