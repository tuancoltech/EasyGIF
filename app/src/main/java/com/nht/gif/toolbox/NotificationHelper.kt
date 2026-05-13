package com.nht.gif.toolbox

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.nht.gif.FileSavedActivity
import com.nht.gif.MyConstants
import com.nht.gif.MySettings
import com.nht.gif.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helpers for creating the file-saved notification channel and posting share notifications.
 *
 * All methods are thread-safe. Channel creation and notification posting are fast system calls
 * that do not block the calling thread regardless of whether it is the main or a background thread.
 */
object NotificationHelper {

  private val nextNotificationId = AtomicInteger(MyConstants.NOTIFICATION_ID_FILE_SAVED_BASE)

  /**
   * Registers the [MyConstants.NOTIFICATION_CHANNEL_FILE_SAVED] notification channel.
   * No-op below API 26. Safe to call multiple times — the system ignores duplicate registrations.
   */
  fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      MyConstants.NOTIFICATION_CHANNEL_FILE_SAVED,
      context.getString(R.string.notification_channel_file_saved_name),
      NotificationManager.IMPORTANCE_DEFAULT
    )
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
  }

  /**
   * Builds an [Intent.ACTION_SEND] intent for sharing [uri] with the given [mimeType].
   * Grants temporary read permission so the receiving app can access the MediaStore URI.
   */
  fun buildShareIntent(uri: Uri, mimeType: String): Intent = Intent().apply {
    action = Intent.ACTION_SEND
    putExtra(Intent.EXTRA_STREAM, uri)
    type = mimeType
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  /**
   * Posts a file-saved notification with a Share action and a tap-to-open content intent.
   *
   * When POST_NOTIFICATIONS is missing on API 33+ and [context] is an [Activity]:
   * - First request / rationale → [onPermissionRequired] is invoked so the caller can drive the
   *   request via [androidx.activity.result.ActivityResultLauncher]. The caller must retry with
   *   [permissionGranted] = true once the permission is granted.
   * - Permanent denial → shows a Snackbar directing the user to app notification settings.
   *
   * [permissionGranted] bypasses the runtime check; use for retry-after-grant and in tests.
   */
  fun showShareNotification(
    context: Context,
    uri: Uri,
    mimeType: String,
    fileName: String,
    permissionGranted: Boolean = isNotificationPermissionGranted(context),
    onPermissionRequired: (() -> Unit)? = null,
  ) {
    if (permissionGranted) {
      postNotification(context, uri, mimeType, fileName)
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context !is Activity) return
    CoroutineScope(Dispatchers.Main.immediate).launch {
      if (context.isFinishing || context.isDestroyed) return@launch
      handleMissingPermission(context, onPermissionRequired)
    }
  }

  private fun handleMissingPermission(activity: Activity, onPermissionRequired: (() -> Unit)?) {
    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
      activity, Manifest.permission.POST_NOTIFICATIONS
    )

    if (shouldShowRationale || !MySettings.notificationPermissionRequested) {
      MySettings.notificationPermissionRequested = true
      onPermissionRequired?.invoke()
    } else {
      Snackbar.make(
        activity.findViewById(android.R.id.content),
        R.string.notifications_blocked_snackbar,
        Snackbar.LENGTH_LONG
      ).setAction(R.string.settings) {
        activity.startActivity(
          Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        )
      }.show()
    }
  }

  private fun postNotification(context: Context, uri: Uri, mimeType: String, fileName: String) {
    val notificationId = nextNotificationId.getAndIncrement()

    val openPendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      FileSavedActivity.createIntent(context, uri),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val sharePendingIntent = PendingIntent.getActivity(
      context,
      notificationId + 1,
      Intent.createChooser(buildShareIntent(uri, mimeType), null)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    val notification = NotificationCompat.Builder(context, MyConstants.NOTIFICATION_CHANNEL_FILE_SAVED)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(context.getString(R.string.notification_file_saved_title, fileName))
      .setContentText(context.getString(R.string.notification_file_saved_body))
      .setContentIntent(openPendingIntent)
      .addAction(0, context.getString(R.string.share), sharePendingIntent)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  private fun isNotificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
