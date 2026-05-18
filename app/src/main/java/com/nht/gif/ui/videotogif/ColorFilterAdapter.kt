package com.nht.gif.ui.videotogif

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nht.gif.R
import com.nht.gif.databinding.ItemColorFilterCardBinding
import com.nht.gif.model.ExportColorFilter

private const val SHIMMER_COLOR_BASE = 0xFF000000.toInt()
private const val SHIMMER_COLOR_HIGH = 0xFFF5F5F5.toInt()
private const val SHIMMER_DURATION_MS = 800L

class ColorFilterAdapter(
  private val onFilterSelected: (ExportColorFilter) -> Unit,
) : RecyclerView.Adapter<ColorFilterAdapter.ViewHolder>() {

  private val items = ExportColorFilter.entries
  private var selectedFilter = ExportColorFilter.NONE
  private var thumbnails: Map<ExportColorFilter, Result<Bitmap>?> = emptyMap()

  fun setSelectedFilter(filter: ExportColorFilter) {
    val prev = selectedFilter
    if (prev == filter) return
    selectedFilter = filter
    notifyItemChanged(items.indexOf(prev))
    notifyItemChanged(items.indexOf(filter))
  }

  fun updateThumbnails(map: Map<ExportColorFilter, Result<Bitmap>?>) {
    thumbnails = map
    notifyItemRangeChanged(0, items.size)
  }

  override fun getItemCount() = items.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    ViewHolder(ItemColorFilterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

  override fun onViewRecycled(holder: ViewHolder) {
    super.onViewRecycled(holder)
    holder.cancelShimmer()
  }

  inner class ViewHolder(private val binding: ItemColorFilterCardBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val strokeWidthSelected =
      binding.root.resources.getDimension(R.dimen.color_filter_card_stroke_width)
    private var shimmerAnimator: ValueAnimator? = null

    fun bind(filter: ExportColorFilter) {
      cancelShimmer()
      binding.mtvFilterName.text = filterLabel(filter)
      binding.sivThumbnail.strokeWidth = if (filter == selectedFilter) strokeWidthSelected else 0f
      when (val state = thumbnails[filter]) {
        null -> showShimmer()
        else -> if (state.isSuccess) showBitmap(state.getOrNull()) else showError()
      }
      binding.root.setOnClickListener {
        if (filter != selectedFilter) {
          val prev = selectedFilter
          selectedFilter = filter
          notifyItemChanged(items.indexOf(prev))
          notifyItemChanged(bindingAdapterPosition)
          onFilterSelected(filter)
        }
      }
    }

    fun cancelShimmer() {
      shimmerAnimator?.cancel()
      shimmerAnimator = null
    }

    private fun showShimmer() {
      val drawable = GradientDrawable().apply { setColor(SHIMMER_COLOR_BASE) }
      binding.sivThumbnail.scaleType = ImageView.ScaleType.FIT_XY
      binding.sivThumbnail.setImageDrawable(drawable)
      shimmerAnimator = ValueAnimator.ofArgb(SHIMMER_COLOR_BASE, SHIMMER_COLOR_HIGH).apply {
        duration = SHIMMER_DURATION_MS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { drawable.setColor(it.animatedValue as Int) }
        start()
      }
    }

    private fun showBitmap(bitmap: Bitmap?) {
      if (bitmap != null) {
        binding.sivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.sivThumbnail.setImageBitmap(bitmap)
      } else {
        showError()
      }
    }

    private fun showError() {
      binding.sivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
      binding.sivThumbnail.setImageResource(R.drawable.baseline_camera_24)
      binding.sivThumbnail.setColorFilter(Color.LTGRAY)
    }

    private fun filterLabel(filter: ExportColorFilter) = binding.root.context.getString(
      when (filter) {
        ExportColorFilter.NONE -> R.string.filter_none
        ExportColorFilter.VINTAGE -> R.string.filter_vintage
        ExportColorFilter.NEON -> R.string.filter_neon
        ExportColorFilter.NOIR -> R.string.filter_noir
      }
    )
  }
}
