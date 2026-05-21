package com.nht.gif.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
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

private val HeaderHeight = 64.dp
private val QrCodeHeight = 144.dp

/**
 * Stateless screen for the ERC-20 donation flow. Shows a description, QR code image, and action
 * buttons. The raw WEBP QR code is decoded on [Dispatchers.IO] via [produceState] to avoid blocking
 * the main thread; the [Image] slot is simply absent until decoding completes.
 *
 * @param onBack       Called when the user taps the header close icon.
 * @param onClose      Called when the user taps the "Close" outlined button.
 * @param onSaveQrCode Called when the user taps "Save QR code".
 */
@Composable
fun DonateCryptoScreen(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSaveQrCode: () -> Unit,
) {
    val context = LocalContext.current
    val qrCodeBitmap by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeStream(context.resources.openRawResource(R.raw.donate_erc20_address))
                .asImageBitmap()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(R.string.close),
                    )
                }
                Text(
                    text = stringResource(R.string.donate_with_crypto),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(thickness = Dimens.dividerThickness)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.largePadding, vertical = Dimens.mediumPadding),
                ) {
                    SelectionContainer {
                        Text(
                            text = stringResource(R.string.donate_with_crypto_line_1),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    qrCodeBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(QrCodeHeight)
                                .padding(top = Dimens.mediumPadding),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                HorizontalDivider(thickness = Dimens.dividerThickness)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.contentPadding)
                        .padding(top = Dimens.smallPadding, bottom = Dimens.contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                    Button(
                        onClick = onSaveQrCode,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Dimens.smallPadding),
                    ) {
                        Text(stringResource(R.string.save_qrcode))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF191C19)
@Composable
private fun DonateCryptoScreenPreview() {
    EasyGifTheme {
        DonateCryptoScreen(
            onBack = {},
            onClose = {},
            onSaveQrCode = {},
        )
    }
}
