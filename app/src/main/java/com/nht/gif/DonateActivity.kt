package com.nht.gif

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nht.gif.toolbox.FileTools.createNewFile
import com.nht.gif.toolbox.Toolbox.toast
import com.nht.gif.ui.DonateScreen
import com.nht.gif.ui.theme.EasyGifTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DonateActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setFinishOnTouchOutside(true)
    setContent {
      EasyGifTheme {
        DonateScreen(
          onClose = ::finish,
          onSaveQrCode = {
            lifecycleScope.launch {
              withContext(Dispatchers.IO) {
                resources.openRawResource(R.raw.donate_buymeacoffee).use { src ->
                  contentResolver.openOutputStream(createNewFile("donate_buy_coffee", "png"))
                    ?.use { dest -> src.copyTo(dest) }
                }
              }
              toast(R.string.donate_qrcode_saved_please_scan_first_image)
            }
          },
        )
      }
    }
  }

  companion object {
    fun start(context: Context) = context.startActivity(Intent(context, DonateActivity::class.java))
  }
}
