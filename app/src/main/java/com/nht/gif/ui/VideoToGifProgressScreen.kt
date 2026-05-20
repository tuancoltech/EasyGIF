package com.nht.gif.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme

/** #050605 — matches @color/white (progress track background) in this project's inverted naming. */
private val ProgressTrackColor = Color(0xFF050605.toInt())

/**
 * Shared stateless composable for dialog-themed activities that show a progress indicator
 * while a background task runs (e.g. transcoding, GIF export).
 *
 * @param title     Label shown above the progress bar. Updated by the host Activity during the task.
 * @param progress  Task progress 0–100, or null while indeterminate.
 * @param onClose   Called when the user taps the close button or triggers back navigation.
 */
@Composable
fun VideoToGifProgressScreen(
    title: String,
    progress: Int?,
    onClose: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.smallPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.contentPadding),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.smallPadding),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ProgressTrackColor,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.smallPadding),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ProgressTrackColor,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_close_24),
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF191C19)
@Composable
private fun VideoToGifProgressScreenIndeterminatePreview() {
    EasyGifTheme {
        VideoToGifProgressScreen(
            title = "Exporting GIF...",
            progress = null,
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF191C19, name = "With progress 42%")
@Composable
private fun VideoToGifProgressScreenDeterminatePreview() {
    EasyGifTheme {
        VideoToGifProgressScreen(
            title = "Exporting GIF... (42%)",
            progress = 42,
            onClose = {},
        )
    }
}
