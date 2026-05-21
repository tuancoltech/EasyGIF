package com.nht.gif

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.nht.gif.ui.EulaScreen
import com.nht.gif.ui.theme.EasyGifTheme

class EulaActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val eulaAlreadyAccepted = MySettings.eulaAccepted
    setContent {
      EasyGifTheme {
        EulaScreen(
          versionName = BuildConfig.VERSION_NAME,
          eulaAlreadyAccepted = eulaAlreadyAccepted,
          onAgree = {
            if (!eulaAlreadyAccepted) {
              MySettings.eulaAccepted = true
              MainActivity.start(this@EulaActivity)
            }
            finish()
          },
          onDisagree = ::finish,
        )
      }
    }
  }


  companion object {
    fun start(context: Context) = context.startActivity(Intent(context, EulaActivity::class.java))
  }
}