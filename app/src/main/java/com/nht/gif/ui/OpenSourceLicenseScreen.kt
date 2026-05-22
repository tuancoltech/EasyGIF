package com.nht.gif.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.nht.gif.R
import com.nht.gif.ui.theme.Dimens
import com.nht.gif.ui.theme.EasyGifTheme

/** #8ff2af — matches android:textColorLink in themes.xml */
private val LinkColor = Color(0xFF8FF2AF.toInt())

/**
 * Stateless screen composable for the Open Source License screen.
 *
 * @param onDone Called when the user taps the Done button.
 */
@Composable
fun OpenSourceLicenseScreen(onDone: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Column {
                    Text(
                        text = stringResource(R.string.source_code_of_easygif_),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Dimens.contentPadding, end = Dimens.contentPadding, top = Dimens.contentPadding),
                    )
                    val sourceUrl = stringResource(R.string.github_com_tasy5kg_easygif)
                    Text(
                        text = buildAnnotatedString {
                            append(sourceUrl)
                            addLink(
                                LinkAnnotation.Url(sourceUrl, TextLinkStyles(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline))),
                                start = 0,
                                end = sourceUrl.length,
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = Dimens.contentPadding, end = Dimens.contentPadding, bottom = Dimens.contentPadding),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)

            SelectionContainer {
                Column {
                    Text(
                        text = stringResource(R.string.open_source_project_used_),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Dimens.contentPadding, end = Dimens.contentPadding, top = Dimens.contentPadding),
                    )
                    val linksRaw = stringResource(R.string._open_source_project_used_links)
                    val links = linksRaw.split("\n")
                    Text(
                        text = buildAnnotatedString {
                            links.forEachIndexed { index, line ->
                                val start = length
                                append(line)
                                addLink(
                                    LinkAnnotation.Url("https://$line", TextLinkStyles(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline))),
                                    start = start,
                                    end = length,
                                )
                                if (index < links.lastIndex) append("\n")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = Dimens.contentPadding, end = Dimens.contentPadding, bottom = Dimens.contentPadding),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dimens.dividerThickness)

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.contentPadding),
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
private fun OpenSourceLicenseScreenPreview() {
    EasyGifTheme {
        OpenSourceLicenseScreen(onDone = {})
    }
}
