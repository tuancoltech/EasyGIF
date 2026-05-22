package com.nht.gif.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stateless screen composable for the Donate screen.
 *
 * Maps activity_donate.xml.
 * Root layout_width=match_parent, layout_height=wrap_content → fillMaxSize (the weight-based
 * NestedScrollView child requires a bounded parent height supplied by the Activity window).
 *
 * @param onClose Called when the user taps the close/back icon or the Close button.
 * @param onSaveQrCode Called when the user taps "Save the QR code".
 */
@Composable
fun DonateScreen(
    onClose: () -> Unit,
    onSaveQrCode: () -> Unit,
) {
    val context = LocalContext.current
    /** Decoded off the main thread — donate_buymeacoffee.webp lives in @raw, not @drawable. */
    val qrCodeBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeStream(
                context.resources.openRawResource(R.raw.donate_buymeacoffee)
            )?.asImageBitmap()
        }
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            /** Header row — mirrors the 64dp horizontal LinearLayoutCompat. */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.donate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = Dimens.dividerThickness,
            )

            /** Scrollable body — mirrors the NestedScrollView (layout_weight=1). */
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.largePadding, vertical = Dimens.mediumPadding),
                ) {
                    Text(
                        text = stringResource(R.string.if_you_like_easygif_please_donate),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    /** Falls back to a 1×1 transparent bitmap while the raw resource is decoding. */
                    Image(
                        bitmap = qrCodeBitmap ?: ImageBitmap(1, 1),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(256.dp)
                            .padding(vertical = Dimens.mediumPadding),
                    )

                    /** textIsSelectable=true on donors title + list only. */
                    SelectionContainer {
                        Column {
                            Text(
                                text = stringResource(R.string.thanks_to_the_following_donated_users),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string._donated_users_list),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.you_can_write_your_nickname),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = Dimens.dividerThickness,
                )

                /** Buttons row — mirrors the bottom horizontal LinearLayoutCompat. */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Dimens.contentPadding,
                            end = Dimens.contentPadding,
                            top = Dimens.smallPadding,
                            bottom = Dimens.contentPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onClose) {
                        Text(stringResource(R.string.close))
                    }
                    Button(
                        onClick = onSaveQrCode,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Dimens.smallPadding),
                    ) {
                        Text(stringResource(R.string.save_donate_qrcode_buy_me_a_coffee))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF191C19)
@Composable
private fun DonateScreenPreview() {
    EasyGifTheme {
        DonateScreen(onClose = {}, onSaveQrCode = {})
    }
}
