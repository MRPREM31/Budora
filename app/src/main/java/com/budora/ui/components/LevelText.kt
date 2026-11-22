package com.budora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.budora.R

/**
 * A battery percentage: the number large, the "%" small on the same baseline.
 * Counts up smoothly from 0 to level on refresh like a speedometer readout.
 */
@Composable
fun LevelText(
    level: Int?,
    color: Color,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
    prefix: String = "",
    refreshTrigger: Int = 0,
) {
    val animInt = remember { Animatable(0f) }
    var lastTrigger by remember { mutableIntStateOf(refreshTrigger) }

    // Speedometer count-up animation: ONLY fires once when user triggers refresh
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > lastTrigger) {
            lastTrigger = refreshTrigger
            animInt.snapTo(0f)
            animInt.animateTo(
                targetValue = (level ?: 0).toFloat(),
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
        }
    }

    // Normal level update (initial load or value change), without continuous looping
    LaunchedEffect(level) {
        if (level != null && level > 0) {
            val target = level.toFloat()
            if (animInt.value == 0f) {
                animInt.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                )
            } else if (animInt.value != target) {
                animInt.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 400),
                )
            }
        }
    }

    val animated = animInt.value.toInt()

    Row(modifier) {
        Text(
            text = if (level != null) "$prefix$animated" else stringResource(R.string.battery_unknown),
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.alignByBaseline(),
        )
        if (level != null) {
            Text(
                text = "%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.alignByBaseline().padding(start = 2.dp),
            )
        }
    }
}
