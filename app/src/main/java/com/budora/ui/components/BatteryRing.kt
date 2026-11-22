package com.budora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * A circular battery gauge with a dynamic Speedometer rev-up animation.
 *
 * @param level 0-100, or null for "no reading".
 * @param refreshTrigger increment to rev up the gauge from 0 to target like a speedometer needle.
 */
@Composable
fun BatteryRing(
    level: Int?,
    color: Color,
    diameter: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val animLevel = remember { Animatable(0f) }
    var lastTrigger by remember { mutableIntStateOf(refreshTrigger) }

    // Speedometer needle rev-up animation: ONLY fires once when user triggers refresh
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > lastTrigger) {
            lastTrigger = refreshTrigger
            animLevel.snapTo(0f)
            animLevel.animateTo(
                targetValue = (level ?: 0).toFloat(),
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
        }
    }

    // Normal level update (initial load or value change), without continuous looping
    LaunchedEffect(level) {
        if (level != null && level > 0) {
            val target = level.toFloat()
            if (animLevel.value == 0f) {
                animLevel.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                )
            } else if (animLevel.value != target) {
                animLevel.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 400),
                )
            }
        }
    }

    val sweep = animLevel.value / 100f * FULL_SWEEP_DEGREES
    val ringColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 400),
        label = "ringColor",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val style = Stroke(width = strokePx, cap = StrokeCap.Round)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = FULL_SWEEP_DEGREES,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = style,
            )

            if (level != null && sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to ringColor,
                        0.5f to lighten(ringColor, GRADIENT_LIFT),
                        1f to ringColor,
                    ),
                    startAngle = RING_START_DEGREES,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = style,
                )
            }
        }
        content()
    }
}

private fun lighten(color: Color, amount: Float) = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount,
    alpha = color.alpha,
)

private const val FULL_SWEEP_DEGREES = 360f
private const val RING_START_DEGREES = -90f
private const val GRADIENT_LIFT = 0.28f
