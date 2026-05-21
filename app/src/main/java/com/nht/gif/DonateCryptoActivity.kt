package com.nht.gif

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nht.gif.toolbox.FileTools
import com.nht.gif.toolbox.Toolbox
import com.nht.gif.ui.DonateCryptoScreen
import com.nht.gif.ui.theme.EasyGifTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DonateCryptoActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setFinishOnTouchOutside(true)
    setContent {
      EasyGifTheme {
        DonateCryptoScreen(
          onBack = ::finish,
          onClose = ::finish,
          onSaveQrCode = ::saveQrCode,
        )
      }
    }
  }

  private fun saveQrCode() {
    lifecycleScope.launch {
      withContext(Dispatchers.IO) {
        resources.openRawResource(R.raw.donate_erc20_address).use { erc20QrCodeImg ->
          contentResolver.openOutputStream(FileTools.createNewFile("donate_erc20", "png"))!!.use { dest ->
            erc20QrCodeImg.copyTo(dest)
          }
        }
      }
      Toolbox.toast(R.string.donation_erc20_qrcode_saved_please_scan_first_image)
    }
  }

  companion object {
    fun start(context: Context) {
      context.startActivity(Intent(context, DonateCryptoActivity::class.java))
    }
  }
}
