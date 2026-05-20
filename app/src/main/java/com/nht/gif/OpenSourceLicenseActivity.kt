package com.nht.gif

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.nht.gif.ui.OpenSourceLicenseScreen
import com.nht.gif.ui.theme.EasyGifTheme

class OpenSourceLicenseActivity : BaseActivity() {

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setContent {
      EasyGifTheme {
        OpenSourceLicenseScreen(onDone = ::finish)
      }
    }
  }

  companion object {
    fun start(context: Context) {
      context.startActivity(Intent(context, OpenSourceLicenseActivity::class.java))
    }
  }
}