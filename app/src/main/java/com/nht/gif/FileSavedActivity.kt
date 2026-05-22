package com.nht.gif

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.nht.gif.MyConstants.EXTRA_SAVED_FILE_URI
import com.nht.gif.toolbox.FileTools
import com.nht.gif.toolbox.FileTools.deleteFile
import com.nht.gif.toolbox.FileTools.fileSize
import com.nht.gif.toolbox.FileTools.formattedFileSize
import com.nht.gif.toolbox.FileTools.mimeType
import com.nht.gif.toolbox.NotificationHelper
import com.nht.gif.toolbox.Toolbox.getExtra
import com.nht.gif.toolbox.Toolbox.toast
import com.nht.gif.ui.FileSavedScreen
import com.nht.gif.ui.theme.EasyGifTheme
import java.util.Locale

class FileSavedActivity : BaseActivity() {
  private val fileUri by lazy { intent.getExtra<Uri>(EXTRA_SAVED_FILE_URI) }

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      NotificationHelper.showShareNotification(
        this, fileUri, fileUri.mimeType() ?: "", FileTools.FileName(fileUri).name,
        permissionGranted = true,
      )
    }
  }

  override fun onCreateIfEulaAccepted(savedInstanceState: Bundle?) {
    setFinishOnTouchOutside(false)
    val mimeType = fileUri.mimeType() ?: throw NotImplementedError()
    setContent {
      EasyGifTheme {
        FileSavedScreen(
          savedLabel = getString(R.string._ext__saved_to_gallery, FileTools.FileName(fileUri).extension.uppercase(Locale.ROOT)),
          fileSize = getString(R.string.file_size_s, fileUri.fileSize().formattedFileSize()),
          mimeType = mimeType,
          fileUri = fileUri,
          onClose = ::finish,
          onDelete = {
            fileUri.deleteFile()
            toast(R.string.file_deleted)
            finish()
          },
          onShare = {
            startActivity(
              Intent.createChooser(
                Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_STREAM, fileUri)
                  type = mimeType
                }, null
              )
            )
          },
          onCopy = { copyToClipboard(fileUri) },
          onDone = ::finish,
          onError = {
            toast(R.string.an_error_occurred)
            finish()
          },
        )
      }
    }
    if (!intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
      NotificationHelper.showShareNotification(
        this, fileUri, mimeType, FileTools.FileName(fileUri).name,
        onPermissionRequired = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
      )
    }
  }

  private fun copyToClipboard(uri: Uri) {
    val clip = ClipData.newUri(contentResolver, getString(R.string.copy_to_clipboard), uri)
    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    // On API 33+ the system shows its own clipboard confirmation UI; avoid duplicate feedback.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(R.string.copied_to_clipboard)
  }

  companion object {
    private const val EXTRA_FROM_NOTIFICATION = "extra_from_notification"

    fun createIntent(context: Context, fileUri: Uri): Intent =
      Intent(context, FileSavedActivity::class.java).putExtra(EXTRA_SAVED_FILE_URI, fileUri)

    fun createIntentFromNotification(context: Context, fileUri: Uri): Intent =
      createIntent(context, fileUri).putExtra(EXTRA_FROM_NOTIFICATION, true)

    fun start(context: Context, fileUri: Uri) = context.startActivity(createIntent(context, fileUri))
  }
}
