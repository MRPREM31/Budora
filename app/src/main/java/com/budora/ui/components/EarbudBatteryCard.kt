package com.budora.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budora.ui.AppIcons
import com.budora.ui.theme.Dimens
import com.budora.ui.theme.batteryColor

@Composable
fun EarbudBatteryCard(
    label: String,
    icon: ImageVector,
    level: Int?,
    isCharging: Boolean,
    ringDiameter: Dp,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
    isInCase: Boolean = false,
    refreshTrigger: Int = 0,
) {
    val color = batteryColor(level)
    val description = buildString {
        append(label)
        append(if (level != null) ", $level percent" else ", level unknown")
        if (isInCase) append(", in case")
        if (isCharging) append(", charging")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(if (isInCase) 0.40f else 1.0f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        BatteryRing(
            level = level,
            color = color,
            diameter = ringDiameter,
            stroke = Dimens.RingStroke,
            refreshTrigger = refreshTrigger,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconEarbud),
                tint = color,
            )
            if (isCharging) ChargingBadge(Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(Dimens.ItemGap))
        LevelText(
            level = level,
            color = color,
            numberSize = numberSize,
            refreshTrigger = refreshTrigger,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = Dimens.LabelTracking,
        )
        if (isInCase) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "IN CASE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChargingBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(BADGE_SIZE)
            .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        Alignment.Center,
    ) {
        Icon(
            AppIcons.Bolt,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private val BADGE_SIZE = 30.dp
