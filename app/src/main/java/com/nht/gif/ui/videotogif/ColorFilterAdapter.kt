package com.nht.gif.ui.videotogif

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nht.gif.R
import com.nht.gif.databinding.ItemColorFilterCardBinding
import com.nht.gif.model.ExportColorFilter

class ColorFilterAdapter(
  private val onFilterSelected: (ExportColorFilter) -> Unit,
) : RecyclerView.Adapter<ColorFilterAdapter.ViewHolder>() {

  private val items = ExportColorFilter.entries
  private var selectedFilter = ExportColorFilter.NONE
  private var thumbnails: Map<ExportColorFilter, Bitmap?> = emptyMap()

  fun setSelectedFilter(filter: ExportColorFilter) {
    val prev = selectedFilter
    if (prev == filter) return
    selectedFilter = filter
    notifyItemChanged(items.indexOf(prev))
    notifyItemChanged(items.indexOf(filter))
  }

  fun updateThumbnails(map: Map<ExportColorFilter, Bitmap?>) {
    thumbnails = map
    notifyItemRangeChanged(0, items.size)
  }

  override fun getItemCount() = items.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    ViewHolder(ItemColorFilterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

  inner class ViewHolder(private val binding: ItemColorFilterCardBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val strokeWidthSelected =
      binding.root.resources.getDimension(R.dimen.color_filter_card_stroke_width)

    fun bind(filter: ExportColorFilter) {
      binding.mtvFilterName.text = filterLabel(filter)
      binding.sivThumbnail.strokeWidth = if (filter == selectedFilter) strokeWidthSelected else 0f
      val bitmap = thumbnails[filter]
      if (bitmap != null) binding.sivThumbnail.setImageBitmap(bitmap)
      else binding.sivThumbnail.setImageDrawable(null)
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
