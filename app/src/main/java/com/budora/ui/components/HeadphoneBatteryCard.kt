package com.budora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budora.R
import com.budora.ui.AppIcons
import com.budora.ui.theme.Dimens
import com.budora.ui.theme.batteryColor

@Composable
fun HeadphoneBatteryCard(
    level: Int?,
    isCharging: Boolean,
    ringDiameter: Dp,
    numberSize: TextUnit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
) {
    val color = batteryColor(level)
    val description = buildString {
        append("Headphones")
        append(if (level != null) ", $level percent" else ", level unknown")
        if (isCharging) append(", charging")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        BatteryRing(
            level = level,
            color = color,
            diameter = ringDiameter * 1.08f,
            stroke = Dimens.RingStroke,
            refreshTrigger = refreshTrigger,
        ) {
            Icon(
                AppIcons.Headphones,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconEarbud * 1.15f),
                tint = color,
            )
            if (isCharging) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
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
        }
        Spacer(Modifier.height(Dimens.ItemGap))
        LevelText(
            level = level,
            color = color,
            numberSize = numberSize,
            refreshTrigger = refreshTrigger,
        )
        Text(
            text = "BATTERY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = Dimens.LabelTracking,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isCharging) "CHARGING ⚡" else "CONNECTED & ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
