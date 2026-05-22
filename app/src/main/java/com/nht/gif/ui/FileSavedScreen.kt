package com.nht.gif.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nht.gif.MyConstants.MIME_TYPE_IMAGE_GIF
import com.nht.gif.MyConstants.MIME_TYPE_IMAGE_WEBP
import com.nht.gif.MyConstants.MIME_TYPE_VIDEO_MP4
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stateless screen composable shown after a file (GIF, WebP, or video) is saved.
 *
 * @param savedLabel Formatted header title, e.g. "GIF saved to gallery".
 * @param fileSize   Formatted file size string, e.g. "File size: 1.23 MB".
 * @param mimeType   MIME type of [fileUri]; controls which preview composable is shown.
 * @param fileUri    URI of the saved file, passed into the preview composable.
 * @param onClose    Called when the user taps the close (×) button.
 * @param onDelete   Called when the user taps the delete button.
 * @param onShare    Called when the user taps the share button.
 * @param onCopy     Called when the user taps the copy button.
 * @param onDone     Called when the user taps Done.
 * @param onError    Called when the WebP preview fails to decode.
 */
@Composable
fun FileSavedScreen(
    savedLabel: String,
    fileSize: String,
    mimeType: String,
    fileUri: Uri,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(R.string.close),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = savedLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = Dimens.dividerThickness,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp),
            ) {
                when (mimeType) {
                    MIME_TYPE_IMAGE_GIF -> GifPreview(
                        fileUri = fileUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.largePadding, vertical = Dimens.mediumPadding),
                    )
                    MIME_TYPE_IMAGE_WEBP -> WebpPreview(
                        fileUri = fileUri,
                        onError = onError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.largePadding, vertical = Dimens.mediumPadding),
                    )
                    MIME_TYPE_VIDEO_MP4 -> VideoPreview(
                        fileUri = fileUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = Dimens.dividerThickness,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.smallPadding),
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_delete_forever_24),
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_share_24),
                        contentDescription = stringResource(R.string.share),
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_content_copy_24),
                        contentDescription = stringResource(R.string.copy_to_clipboard),
                    )
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.padding(end = Dimens.smallPadding),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Composable
private fun GifPreview(fileUri: Uri, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            AppCompatImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                Glide.with(ctx)
                    .load(fileUri)
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(this)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun WebpPreview(fileUri: Uri, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AndroidView(
        factory = { ctx ->
            AppCompatImageView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ImageDecoder.decodeDrawable(
                                ImageDecoder.createSource(context.contentResolver, fileUri)
                            )
                        }
                    }.onSuccess { drawable ->
                        setImageDrawable(drawable)
                        (drawable as? AnimatedImageDrawable)?.start()
                    }.onFailure {
                        onError()
                    }
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun VideoPreview(fileUri: Uri, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoViewRef = remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val vv = videoViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> vv.pause()
                Lifecycle.Event.ON_RESUME -> vv.start()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).also { vv ->
                videoViewRef.value = vv
                vv.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                vv.setOnPreparedListener { mp ->
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                    mp.isLooping = true
                }
                vv.setVideoURI(fileUri)
                vv.start()
            }
        },
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun FileSavedScreenPreview() {
    EasyGifTheme {
        FileSavedScreen(
            savedLabel = "GIF saved to gallery",
            fileSize = "File size: 1.23 MB",
            mimeType = MIME_TYPE_IMAGE_GIF,
            fileUri = Uri.EMPTY,
            onClose = {},
            onDelete = {},
            onShare = {},
            onCopy = {},
            onDone = {},
            onError = {},
        )
    }
}
