package com.nht.gif.ui.videotogif

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nht.gif.R
import com.nht.gif.databinding.ItemLoopModeCardBinding
import com.nht.gif.model.ExportLoopMode

class LoopModeAdapter(
  private val onModeSelected: (ExportLoopMode) -> Unit,
) : RecyclerView.Adapter<LoopModeAdapter.ViewHolder>() {

  private val items = ExportLoopMode.entries
  private var selectedMode = ExportLoopMode.FORWARD

  fun setSelectedMode(mode: ExportLoopMode) {
    val prev = selectedMode
    if (prev == mode) return
    selectedMode = mode
    notifyItemAtSafe(items.indexOf(prev))
    notifyItemAtSafe(items.indexOf(mode))
  }

  override fun getItemCount() = items.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    ViewHolder(ItemLoopModeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

  private fun notifyItemAtSafe(index: Int) {
    if (index in items.indices) notifyItemChanged(index)
  }

  inner class ViewHolder(private val binding: ItemLoopModeCardBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(mode: ExportLoopMode) {
      binding.root.isSelected = mode == selectedMode
      binding.ivLoopModeIcon.setImageResource(iconRes(mode))
      binding.mtvLoopModeName.setText(labelRes(mode))
      binding.root.setOnClickListener {
        if (mode != selectedMode) {
          val prev = selectedMode
          selectedMode = mode
          notifyItemAtSafe(items.indexOf(prev))
          notifyItemAtSafe(bindingAdapterPosition)
          onModeSelected(mode)
        }
      }
    }

    private fun iconRes(mode: ExportLoopMode) = when (mode) {
      ExportLoopMode.FORWARD -> R.drawable.ic_loop_forward
      ExportLoopMode.REVERSE -> R.drawable.ic_loop_reverse
      ExportLoopMode.BOOMERANG -> R.drawable.ic_loop_boomerang
    }

    private fun labelRes(mode: ExportLoopMode) = when (mode) {
      ExportLoopMode.FORWARD -> R.string.loop_mode_forward
      ExportLoopMode.REVERSE -> R.string.loop_mode_reverse
      ExportLoopMode.BOOMERANG -> R.string.loop_mode_boomerang
    }
  }
}
