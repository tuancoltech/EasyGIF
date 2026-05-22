package com.nht.gif

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nht.gif.ui.PlaybackSpeedScreen
import com.nht.gif.ui.theme.EasyGifTheme

class BottomSheetVideoToGifPlaybackSpeed : BottomSheetDialogFragment() {
  private val videoToGifActivity get() = activity as VideoToGifActivity

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        EasyGifTheme {
          PlaybackSpeedScreen(
            onSpeedChange = { speed, label -> videoToGifActivity.setPlaybackSpeed(speed, label) },
          )
        }
      }
    }
  }

  companion object {
    const val TAG = "BottomSheetVideoToGifPlaybackSpeed"
  }
}
