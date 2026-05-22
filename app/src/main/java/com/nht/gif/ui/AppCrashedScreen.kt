package com.nht.gif.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme

/**
 * Stateless screen composable for the app-crashed dialog.
 *
 * @param problemLog Full crash report text (stack trace + system/app info).
 * @param onCopy Called when the user taps "Copy to clipboard".
 * @param onExit Called when the user taps "Exit".
 */
@Composable
fun AppCrashedScreen(
    problemLog: String,
    onCopy: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                Text(
                    text = stringResource(R.string.sorry_easygif_error),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        horizontal = Dimens.largePadding,
                        vertical = Dimens.mediumPadding,
                    ),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.largePadding, vertical = Dimens.mediumPadding),
            ) {
                Text(
                    text = stringResource(R.string.error_log),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCopy) {
                    Text(stringResource(R.string.copy_to_clipboard))
                }
            }
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = problemLog,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.largePadding)
                        .padding(bottom = Dimens.smallPadding),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.contentPadding, vertical = Dimens.smallPadding),
            ) {
                Text(
                    text = stringResource(R.string.exit),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppCrashedScreenPreview() {
    EasyGifTheme {
        AppCrashedScreen(
            problemLog = "[Exception Info]\njava.lang.IllegalArgumentException: example\n\tat com.nht.gif.MainActivity.onCreate(MainActivity.kt:102)\n\n[System Info]\nAndroid SDK Version = 31\nModel = Pixel 6\n\n[Application Info]\nVersion Name = 1.0.0",
            onCopy = {},
            onExit = {},
        )
    }
}
