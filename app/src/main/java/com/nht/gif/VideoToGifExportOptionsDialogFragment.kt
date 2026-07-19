package com.nht.gif

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.drawable.Animatable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.nht.gif.model.EstimationState
import com.nht.gif.model.ExportColorFilter
import com.nht.gif.model.ExportLoopMode
import com.nht.gif.model.OutputFormat
import com.nht.gif.model.QualityTier
import com.nht.gif.model.WebpQuality
import com.nht.gif.toolbox.collectOnStarted
import com.nht.gif.toolbox.launchOnStarted
import com.nht.gif.ui.videotogif.ColorFilterAdapter
import com.nht.gif.ui.videotogif.LoopModeAdapter
import com.nht.gif.ui.videotogif.PreviewController
import com.nht.gif.ui.videotogif.VideoToGifExportOptionsViewModel
import com.nht.gif.databinding.DialogFragmentVideoToGifExportOptionsBinding
import com.nht.gif.toolbox.MediaTools.getVideoSingleFrame
import com.nht.gif.toolbox.Toolbox.backgroundColor
import com.nht.gif.toolbox.Toolbox.colorIntToHex
import com.nht.gif.toolbox.Toolbox.constraintBy
import com.nht.gif.toolbox.Toolbox.joinToStringSpecial
import com.nht.gif.toolbox.Toolbox.logRed
import com.nht.gif.toolbox.Toolbox.onClick
import com.nht.gif.toolbox.Toolbox.toast
import com.nht.gif.toolbox.Toolbox.visibleIf
import kotlin.math.min

/**
 * Dialog fragment that presents all export-option controls for the Video-to-GIF pipeline.
 *
 * Collects the user's current UI selections and assembles a [TaskBuilderVideoToGif], then hands
 * it off to [VideoToGifPerformerActivity] to run the FFmpeg encoding pipeline. Heavy state logic
 * (file-size estimation, filter thumbnail generation, Smart Trim detection) is delegated to
 * [VideoToGifExportOptionsViewModel]. Preview rendering is delegated to [PreviewController].
 */
class VideoToGifExportOptionsDialogFragment : DialogFragment() {
  /** View binding backing field; non-null only between [onCreateView] and [onDestroyView]. */
  private var _binding: DialogFragmentVideoToGifExportOptionsBinding? = null
  private val binding get() = _binding!!
  /** Typed shorthand to the host activity, cast once per property access. */
  private val vtgActivity get() = activity as VideoToGifActivity
  /** Single video frame captured at the current playback position, pre-composited with the text
   *  overlay and cropped to the user's crop rectangle. Serves as the base for the static preview. */
  private lateinit var frame: Bitmap
  /** Manages the FFmpeg preview-rendering pipeline and its associated bitmap/file caches.
   *  Non-null only between [onViewCreated] (after the frame is ready) and [onDestroyView]. */
  private var previewController: PreviewController? = null
  private val viewModel: VideoToGifExportOptionsViewModel by lazy {
    ViewModelProvider(
      this,
      VideoToGifExportOptionsViewModel.factory(
        inputVideoPath = vtgActivity.inputVideoPath,
        duration = vtgActivity.videoView.duration,
        cropParams = vtgActivity.cropParams,
        outputSpeed = vtgActivity.playbackSpeed,
      )
    )[VideoToGifExportOptionsViewModel::class.java]
  }

  /** RecyclerView adapter for the horizontal colour-filter card list. */
  private lateinit var colorFilterAdapter: ColorFilterAdapter
  /** RecyclerView adapter for the horizontal loop-mode card list. */
  private lateinit var loopModeAdapter: LoopModeAdapter

  /** Inflates the dialog layout and returns the root view. All view setup is deferred to [onViewCreated]. */
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    _binding = DialogFragmentVideoToGifExportOptionsBinding.inflate(layoutInflater, container, false)
    return binding.root
  }

  /**
   * Wires all UI controls to the ViewModel, registers state observers, and renders the initial
   * static preview frame. Dismisses immediately if the host activity's video player is not yet
   * ready, to avoid a crash on the range-slider values that are only populated after
   * [VideoToGifActivity.mediaPlayerReady] is called.
   */
  @SuppressLint("ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // The system can restore this dialog fragment (e.g. after a permission-revoke restart) before
    // VideoToGifActivity.mediaPlayerReady() has been called. At that point the rangeSlider has only
    // its XML-default single-value list, so createTaskBuilder() would crash on values[1].
    // Dismiss immediately and let the user re-open the dialog once the activity is fully ready.
    if (!vtgActivity.isVideoReady) {
      dismissAllowingStateLoss()
      return
    }

    vtgActivity.videoView.pause()
    binding.mbSave.onClick {
      vtgActivity.videoView.pause()
      val trimTime = with(vtgActivity.rangeSlider) {
        if ((values[0] * 100).toInt() == 0 && (values[1] * 100).toInt() == vtgActivity.videoView.duration) null
        else ((values[0] * 100).toInt() to (values[1] * 100).toInt())
      }
      viewModel.requestSave(
        trimStartMs = trimTime?.first?.toLong() ?: 0L,
        trimEndMs = trimTime?.second?.toLong(),
      )
    }
    collectOnStarted(viewModel.isDetecting) { detecting ->
      if (detecting) {
        val spec = CircularProgressIndicatorSpec(
          requireContext(), null, 0,
          com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall,
        )
        val drawable = IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
        drawable.start()
        binding.mbSave.icon = drawable
        binding.mbSave.isEnabled = false
      } else {
        (binding.mbSave.icon as? Animatable)?.stop()
        binding.mbSave.icon = null
        binding.mbSave.isEnabled = true
      }
    }
    collectOnStarted(viewModel.smartTrimEvent) { event ->
      when (event) {
        is VideoToGifExportOptionsViewModel.SmartTrimEvent.Proceed -> {
          val taskBuilder = createTaskBuilder()
          val finalTaskBuilder = event.overrideEndMs?.let { endMs ->
            taskBuilder.copy(
              input = taskBuilder.input.copy(
                trimTime = taskBuilder.input.trimTime?.copy(second = endMs.toInt()) ?: (0 to endMs.toInt())
              )
            )
          } ?: taskBuilder
          VideoToGifPerformerActivity.start(vtgActivity, finalTaskBuilder)
        }
        is VideoToGifExportOptionsViewModel.SmartTrimEvent.ConfirmSmartTrim ->
          showSmartTrimDialog(event.originalEndMs, event.detectedEndMs)
      }
    }
    binding.mbtgOutputFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (isChecked) {
        val format = if (checkedId == binding.mbOutputFormatWebp.id) OutputFormat.ANIMATED_WEBP else OutputFormat.GIF
        viewModel.setOutputFormat(format)
      }
    }
    launchOnStarted {
      launch {
        viewModel.outputFormat.collect { format ->
          val isGif = format == OutputFormat.GIF
          binding.llcRowGifImageQuality.visibleIf { isGif }
          binding.dividerGifControls.root.visibleIf { isGif }
          binding.llcRowGifColorQuality.visibleIf { isGif }
          binding.chipEnableFinalDelay.visibleIf { isGif }
          binding.llcWebpQualitySection.visibleIf { !isGif }
          val activeColor = ContextCompat.getColor(requireContext(), R.color.green_dark)
          val inactiveColor = ContextCompat.getColor(requireContext(), R.color.grey)
          TextViewCompat.setTextAppearance(binding.mtvEstimatedGifSize,
            if (isGif) R.style.TextAppearance_App_EstSize_Active
            else R.style.TextAppearance_App_EstSize_Inactive)
          binding.mtvEstimatedGifSize.setTextColor(if (isGif) activeColor else inactiveColor)
          TextViewCompat.setTextAppearance(binding.mtvEstimatedWebpSize,
            if (isGif) R.style.TextAppearance_App_EstSize_Inactive
            else R.style.TextAppearance_App_EstSize_Active)
          binding.mtvEstimatedWebpSize.setTextColor(if (isGif) inactiveColor else activeColor)
        }
      }
      launch {
        viewModel.webpQuality.collect { quality ->
          val targetId = when (quality) {
            WebpQuality.SMALL -> binding.mbWebpQualitySmall.id
            WebpQuality.MEDIUM -> binding.mbWebpQualityMedium.id
            WebpQuality.HIGH -> binding.mbWebpQualityHigh.id
            WebpQuality.LOSSLESS -> binding.mbWebpQualityLossless.id
          }
          if (binding.mbtgWebpQuality.checkedButtonId != targetId) {
            binding.mbtgWebpQuality.check(targetId)
          }
        }
      }
      launch {
        viewModel.showLosslessWarning.collect { show ->
          binding.mtvLosslessWarning.visibleIf { show }
        }
      }
      launch {
        viewModel.estimationState.collect { state ->
          val isLoading = state is EstimationState.Loading
          binding.cpiEstimation.visibleIf { isLoading }
          binding.mtvEstimatedGifSize.visibleIf { !isLoading }
          binding.mtvEstimatedWebpSize.visibleIf { !isLoading }
          if (!isLoading) {
            val isGif = viewModel.outputFormat.value == OutputFormat.GIF
            val gifText = viewModel.gifSizeText.value
            val webpText = viewModel.webpSizeText.value
            binding.mtvEstimatedGifSize.text =
              if (!isGif) "$gifText · ${viewModel.clarityTier.value.toResString()}" else gifText
            binding.mtvEstimatedWebpSize.text =
              if (isGif) "$webpText · ${viewModel.webpQualityTier.value.toResString()}" else webpText
          }
        }
      }
    }
    binding.mbtgWebpQuality.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (isChecked) {
        val quality = when (checkedId) {
          binding.mbWebpQualitySmall.id -> WebpQuality.SMALL
          binding.mbWebpQualityMedium.id -> WebpQuality.MEDIUM
          binding.mbWebpQualityHigh.id -> WebpQuality.HIGH
          binding.mbWebpQualityLossless.id -> WebpQuality.LOSSLESS
          else -> return@addOnButtonCheckedListener
        }
        if (viewModel.webpQuality.value != quality) viewModel.setWebpQuality(quality)
      }
    }
    binding.chipGroupMoreOptions.setOnCheckedStateChangeListener { _, checkedIds ->
      val chipEffectNeedsToBeViewedAfterExporting = listOf(
        binding.chipFramerate, binding.chipEnableFinalDelay
      )
      val checkedChips = chipEffectNeedsToBeViewedAfterExporting.filter { checkedIds.contains(it.id) }
      if (checkedChips.isEmpty()) {
        binding.mtvMoreOptionsTips.visibility = GONE
      } else {
        binding.mtvMoreOptionsTips.text = getString(
          R.string.effect_needs_to_be_viewed_after_exporting,
          checkedChips.map { it.text }.joinToStringSpecial(getString(R.string.language_item_separator_normal), getString(R.string.language_item_separator_last))
        )
        binding.mtvMoreOptionsTips.visibility = VISIBLE
      }
    }
    binding.acivSingleFramePreview.setOnTouchListener { _, event ->
      if (binding.chipEnableColorKey.isChecked && event.pointerCount == 1) {
        val eventXY = floatArrayOf(event.x, event.y)
        val invertMatrix = Matrix()
        binding.acivSingleFramePreview.imageMatrix.invert(invertMatrix)
        invertMatrix.mapPoints(eventXY)
        val bitmap = previewController?.render(createTaskBuilder().getForPreviewOnly().copy(colorKey = null))
          ?: return@setOnTouchListener true
        logRed("(v.drawable as BitmapDrawable).bitmap", "${bitmap.width}x${bitmap.height}")
        val x = eventXY[0].toInt().constraintBy(0 until bitmap.width)
        val y = eventXY[1].toInt().constraintBy(0 until bitmap.height)
        binding.viewColorKeyIndicator.backgroundColor = bitmap[x, y]
        binding.mcbColorKeyPreview.isChecked = true
        updatePreviewImage()
      }
      true
    }
    binding.mtvFramerateOver10Warning.visibleIf { createTaskBuilder().config.fps > 10 }
    binding.chipEnableColorKey.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupColorKey.visibleIf { isChecked }
      updatePreviewImage()
    }
    binding.chipFramerate.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupFramerate.visibleIf { isChecked }
    }
    binding.chipColorFilter.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupColorFilter.visibleIf { isChecked }
      if (isChecked) viewModel.loadThumbnails()
    }
    binding.chipLoopMode.setOnCheckedChangeListener { _, isChecked ->
      binding.llcGroupLoopMode.visibleIf { isChecked }
    }
    loopModeAdapter = LoopModeAdapter { mode -> viewModel.setLoopMode(mode) }
    binding.rvLoopModeOptions.adapter = loopModeAdapter
    colorFilterAdapter = ColorFilterAdapter { filter -> viewModel.setColorFilter(filter) }
    binding.rvColorFilterOptions.adapter = colorFilterAdapter
    collectOnStarted(viewModel.colorFilter) { filter ->
      colorFilterAdapter.setSelectedFilter(filter)
      binding.chipColorFilter.text = when (filter) {
        ExportColorFilter.NONE -> getString(R.string.color_filter)
        ExportColorFilter.VINTAGE -> getString(R.string.filter_vintage)
        ExportColorFilter.NEON -> getString(R.string.filter_neon)
        ExportColorFilter.NOIR -> getString(R.string.filter_noir)
      }
      updatePreviewImage()
    }
    collectOnStarted(viewModel.filterThumbnails) { thumbnails ->
      colorFilterAdapter.updateThumbnails(thumbnails)
    }
    collectOnStarted(viewModel.loopMode) { mode ->
      loopModeAdapter.setSelectedMode(mode)
      binding.tvBoomerangSizeWarning.visibleIf { mode == ExportLoopMode.BOOMERANG }
      // Smart Trim temporarily disabled — rowSmartTrim stays gone until the feature is re-enabled
      // binding.rowSmartTrim.visibleIf { mode == ExportLoopMode.REVERSE || mode == ExportLoopMode.BOOMERANG }
      binding.chipLoopMode.text = when (mode) {
        ExportLoopMode.FORWARD -> getString(R.string.loop_mode)
        ExportLoopMode.REVERSE -> getString(R.string.loop_mode_reverse)
        ExportLoopMode.BOOMERANG -> getString(R.string.loop_mode_boomerang)
      }
    }
    binding.switchSmartTrim.setOnCheckedChangeListener { _, isChecked ->
      viewModel.setSmartTrimEnabled(isChecked)
    }
    binding.viewColorKeyIndicator.onClick { toast(R.string.click_on_the_preview_image_to_pick_an_color) }
    binding.sliderColorKeySimilarity.apply {
      setLabelFormatter { "${it.toInt()}%" }
      addOnChangeListener { _, _, _ ->
        performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        updatePreviewImage()
      }
    }
    binding.tietResolutionInputValue.doAfterTextChanged {
      updatePreviewImage()
      viewModel.setShortLength(getSelectedShortLength())
    }
    binding.mbtgColorQuality.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        updatePreviewImage()
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        val colorQuality = when (checkedId) {
          binding.mbColorQualityLow.id -> 32
          binding.mbColorQualityMid.id -> 64
          binding.mbColorQualityHigh.id -> 128
          binding.mbColorQualityMax.id -> 256
          else -> return@addOnButtonCheckedListener
        }
        viewModel.setColorQuality(colorQuality)
      }
    }

    binding.mbtgResolution.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        binding.llcGroupResolutionInput.visibleIf { checkedId == binding.mbResolutionCustom.id }
        if (checkedId == binding.mbResolutionCustom.id) binding.tietResolutionInputValue.requestFocus()
        updatePreviewImage()
        viewModel.setShortLength(getSelectedShortLength())
      }
    }
    binding.mbtgImageQuality.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        updatePreviewImage()
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        val lossy = when (checkedId) {
          binding.mbImageQualityLow.id -> 200
          binding.mbImageQualityMid.id -> 70
          binding.mbImageQualityHigh.id -> 30
          binding.mbImageQualityMax.id -> null
          else -> return@addOnButtonCheckedListener
        }
        viewModel.setLossy(lossy)
      }
    }
    binding.mbtgFramerate.addOnButtonCheckedListener { group, checkedId, isChecked ->
      if (isChecked) {
        binding.mtvFramerateOver10Warning.visibleIf { createTaskBuilder().config.fps > 10 }
        group.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
        val fps = when (checkedId) {
          binding.mbFramerate5.id -> 5
          binding.mbFramerate10.id -> 10
          binding.mbFramerate16.id -> 16
          binding.mbFramerate25.id -> 25
          binding.mbFramerate50.id -> 50
          else -> return@addOnButtonCheckedListener
        }
        viewModel.setFps(fps)
      }
    }
    binding.mcbColorKeyPreview.setOnCheckedChangeListener { buttonView, _ ->
      buttonView.performHapticFeedback(HapticFeedbackType.SWITCH_TOGGLING)
      updatePreviewImage()
    }
    binding.mbClose.onClick { dismiss() }
    viewLifecycleOwner.lifecycleScope.launch {
      frame = getVideoSingleFrame(
        vtgActivity.inputVideoPath, vtgActivity.videoView.currentPosition.toLong()
      ) ?: run {
        toast(R.string.unable_to_read_video)
        dismissAllowingStateLoss()
        return@launch
      }
      Canvas(frame).drawBitmap(
        TextRender.render(vtgActivity.textRender, frame.width, frame.height), 0f, 0f, null
      ) // Merge the text layer with the frame
      frame = vtgActivity.cropParams.crop(frame) // Crop
      previewController = PreviewController(frame, vtgActivity.cropParams)
      binding.viewColorKeyIndicator.backgroundColor = vtgActivity.savedColorKeyColor ?: frame[0, 0]
      updatePreviewImage()
    }
  }

  /**
   * Assembles a [TaskBuilderVideoToGif] from the current UI state. View-only values (trim range,
   * text overlay, video dimensions, final-delay flag, colour-key) are read here and forwarded to
   * [VideoToGifExportOptionsViewModel.buildTask], which fills in the remaining parameters from
   * its own state.
   */
  private fun createTaskBuilder() = viewModel.buildTask(
    trimTime = with(vtgActivity.rangeSlider) {
      if ((values[0] * 100).toInt() == 0 && (values[1] * 100).toInt() == vtgActivity.videoView.duration) null
      else ((values[0] * 100).toInt() to (values[1] * 100).toInt())
    },
    textRender = vtgActivity.textRender,
    videoWH = vtgActivity.videoWH,
    finalDelay = if (binding.chipEnableFinalDelay.isChecked) 50 else -1,
    colorKey = with(binding) {
      if (chipEnableColorKey.isChecked)
        (viewColorKeyIndicator.backgroundColor.colorIntToHex() to sliderColorKeySimilarity.value.toInt())
      else null
    },
  )

  /** Rebuilds and displays the preview bitmap from the current UI state, respecting the
   *  colour-key preview toggle: when the toggle is off, colour-key is excluded from rendering. */
  private fun updatePreviewImage() {
    val controller = previewController ?: return
    val taskBuilder = createTaskBuilder().getForPreviewOnly()
    binding.acivSingleFramePreview.setImageBitmap(
      controller.render(
        if (binding.chipEnableColorKey.isChecked && binding.mcbColorKeyPreview.isChecked) taskBuilder
        else taskBuilder.copy(colorKey = null)
      )
    )
  }

  /** Reads the resolution toggle group and optional custom text field to return the currently
   *  selected short-side pixel count. The result is clamped to an even number within
   *  `[2, min(outW, outH)]` to satisfy FFmpeg's scale filter constraints. */
  private fun getSelectedShortLength() =
    when (binding.mbtgResolution.checkedButtonId) {
      binding.mbResolution144p.id -> 144
      binding.mbResolution240p.id -> 240
      binding.mbResolution320p.id -> 320
      binding.mbResolutionCustom.id -> {
        val inputValue = ("0" + binding.tietResolutionInputValue.text.toString()).toInt()
        if (inputValue == 0) 240 else if (inputValue % 2 == 0) inputValue else inputValue + 1
      }

      else -> throw IllegalArgumentException()
    }.constraintBy(2..min(vtgActivity.cropParams.outW, vtgActivity.cropParams.outH))

  /** Maps a [QualityTier] to its localised label string for display in the size estimate row. */
  private fun QualityTier.toResString() = when (this) {
    QualityTier.LOW -> getString(R.string.low)
    QualityTier.MID -> getString(R.string.mid)
    QualityTier.HIGH -> getString(R.string.high)
    QualityTier.MAX -> getString(R.string.max)
    QualityTier.BEST -> getString(R.string.best)
  }

  /**
   * Persists the current colour-key indicator colour back to the activity for restoration if the
   * dialog is reopened, then clears all preview bitmaps and cached files. Resumes video playback
   * only if the activity was fully ready when the dialog opened.
   */
  override fun onDestroyView() {
    vtgActivity.savedColorKeyColor = binding.viewColorKeyIndicator.backgroundColor
    super.onDestroyView()
    _binding = null
    previewController?.clear()
    previewController = null
    // Only resume playback if the video was actually prepared; if we dismissed early due to the
    // activity not being ready, starting playback here would be premature.
    if (vtgActivity.isVideoReady) vtgActivity.videoView.start()
  }

  /**
   * Shows a Material alert dialog presenting the Smart Trim detection result. Routes the user's
   * choice ("Use Smart Trim" / "Use My Trim") back to the ViewModel via
   * [VideoToGifExportOptionsViewModel.onSmartTrimDialogResult].
   */
  private fun showSmartTrimDialog(originalEndMs: Long, detectedEndMs: Long) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.smart_trim_dialog_title)
      .setMessage(getString(
        R.string.smart_trim_dialog_message,
        "%.1fs".format(detectedEndMs / 1000f),
        "%.1fs".format(originalEndMs / 1000f),
      ))
      .setPositiveButton(R.string.smart_trim_use_smart) { _, _ ->
        viewModel.onSmartTrimDialogResult(
          useSmartTrim = true,
          originalEndMs = originalEndMs,
          detectedEndMs = detectedEndMs,
        )
      }
      .setNegativeButton(R.string.smart_trim_use_mine) { _, _ ->
        viewModel.onSmartTrimDialogResult(
          useSmartTrim = false,
          originalEndMs = originalEndMs,
          detectedEndMs = detectedEndMs,
        )
      }
      .show()
  }

  companion object {
    /** Stable tag used for [androidx.fragment.app.FragmentManager] `findFragmentByTag` / `show` lookups. */
    const val TAG = "VideoToGifExportOptionsDialogFragment"
  }
}
