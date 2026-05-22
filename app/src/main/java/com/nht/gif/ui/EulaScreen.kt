package com.nht.gif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme

/**
 * Stateless screen composable for the EULA / Privacy Policy dialog.
 *
 * @param versionName App version string shown in the header (e.g. "1.0.0").
 * @param eulaAlreadyAccepted True when the user has previously accepted — switches the UI to
 *   read-only mode (hides the Disagree button, changes Agree label to Close).
 * @param onAgree Called when the user taps Agree (first-time) or Close (already accepted).
 * @param onDisagree Called when the user taps Exit; only reachable when [eulaAlreadyAccepted] is false.
 */
@Composable
fun EulaScreen(
    versionName: String,
    eulaAlreadyAccepted: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.contentPadding, vertical = 32.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimens.contentPadding,
                            vertical = Dimens.mediumPadding,
                        ),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            AppCompatImageView(ctx).apply {
                                setImageResource(R.mipmap.ic_launcher)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    )
                    Column(modifier = Modifier.padding(start = Dimens.mediumPadding)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.version_X, versionName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)

                SelectionContainer {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Dimens.contentPadding,
                            vertical = Dimens.mediumPadding,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string._eula_),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.eula_line_1),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = stringResource(R.string.eula_line_2),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.eula_line_addition),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.eula_line_3),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string._privacy_policy_),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = stringResource(R.string.privacy_policy_line_1),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = stringResource(
                                if (eulaAlreadyAccepted) R.string.you_have_read_agreed_eula_to_withdraw
                                else R.string.accept_license_above_to_use,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimens.contentPadding,
                            vertical = Dimens.smallPadding,
                        ),
                ) {
                    if (!eulaAlreadyAccepted) {
                        OutlinedButton(onClick = onDisagree) {
                            Text(
                                text = stringResource(R.string.exit),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(Dimens.smallPadding))
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onAgree()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (eulaAlreadyAccepted) R.string.close else R.string.agree))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EulaScreenFirstTimePreview() {
    EasyGifTheme {
        EulaScreen(
            versionName = "1.0.0",
            eulaAlreadyAccepted = false,
            onAgree = {},
            onDisagree = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EulaScreenAlreadyAcceptedPreview() {
    EasyGifTheme {
        EulaScreen(
            versionName = "1.0.0",
            eulaAlreadyAccepted = true,
            onAgree = {},
            onDisagree = {},
        )
    }
}
