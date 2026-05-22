package com.nht.gif.ui

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nht.gif.R
import com.nht.gif.ui.theme.EasyGifTheme
import kotlin.math.roundToInt

/**
 * Stateful screen composable for the playback speed bottom sheet.
 *
 * Maps bottom_sheet_video_to_gif_playback_speed.xml.
 * Root layout_width=match_parent, layout_height=match_parent → fillMaxWidth (height controlled by BottomSheetDialog).
 *
 * @param initialStep Initial slider position (0–7), where 2 = 1X (normal speed).
 * @param onSpeedChange Called on each discrete step change. Returns true if the speed was applied
 *   successfully; false if a warning should be shown (e.g. speed too slow for the clip).
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PlaybackSpeedScreen(
    initialStep: Int = 2,
    onSpeedChange: (speed: Float, label: String) -> Boolean,
) {
    var sliderStep by remember { mutableIntStateOf(initialStep) }
    var showWarning by remember { mutableStateOf(false) }
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 48.dp),
    ) {
        /** Drag handle — mirrors BottomSheetDragHandleView tinted @color/green_light. */
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }

        /** Title + slider row — mirrors the inner horizontal LinearLayoutCompat (weight 1:4). */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.playback_speed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            /** Stable replacement for labelBehavior="visible": label circle positioned above the thumb
             *  via BoxWithConstraints, avoiding the experimental thumb parameter on Slider. */
            Column(modifier = Modifier.weight(4f)) {
                val labelSize = 40.dp
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(labelSize),
                ) {
                    val fraction = sliderStep.toFloat() / 7f
                    val sliderHPad = 10.dp
                    val thumbCenterX = sliderHPad + (maxWidth - sliderHPad * 2) * fraction
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(labelSize)
                            .offset(x = thumbCenterX - labelSize / 2),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = sliderValueToText(sliderStep),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                }
                Slider(
                    value = sliderStep.toFloat(),
                    onValueChange = { raw ->
                        val step = raw.roundToInt()
                        if (step != sliderStep) {
                            sliderStep = step
                            showWarning = !onSpeedChange(sliderValueToSpeed(step), sliderValueToText(step))
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = 0f..7f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        activeTickColor = MaterialTheme.colorScheme.tertiary,
                    ),
                )
            }
        }

        /** Warning — replaces mtvSpeedWarning gone/visible with AnimatedVisibility. */
        AnimatedVisibility(visible = showWarning) {
            Text(
                text = stringResource(R.string.speed_warning_slower),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
            )
        }
    }
}

private fun sliderValueToSpeed(step: Int) = when (step) {
    0 -> 0.5f
    1 -> 0.75f
    2 -> 1f
    3 -> 1.25f
    4 -> 1.5f
    5 -> 2f
    6 -> 3f
    7 -> 4f
    else -> throw IllegalArgumentException("Invalid slider step: $step")
}

private fun sliderValueToText(step: Int) = when (step) {
    0 -> "0.5X"
    1 -> "0.75X"
    2 -> "1X"
    3 -> "1.25X"
    4 -> "1.5X"
    5 -> "2X"
    6 -> "3X"
    7 -> "4X"
    else -> throw IllegalArgumentException("Invalid slider step: $step")
}

@Preview(showBackground = true, backgroundColor = 0xFF191C19)
@Composable
private fun PlaybackSpeedScreenPreview() {
    EasyGifTheme {
        PlaybackSpeedScreen(onSpeedChange = { _, _ -> true })
    }
}
