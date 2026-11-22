package com.budora.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budora.R
import com.budora.data.LogoShape
import com.budora.ui.AppIcons

/**
 * Full-screen pro crop page with real-time shape selection, smooth pan & zoom,
 * rule-of-thirds grid overlay, and comfortable, guaranteed navigation-bar clearance.
 */
@Composable
fun LogoCropDialog(
    sourceBitmap: Bitmap,
    initialShape: LogoShape = LogoShape.ROUND,
    onDismiss: () -> Unit,
    onCropped: (Bitmap, LogoShape) -> Unit,
) {
    val boxSizeDp = 210.dp
    val boxPx = with(LocalDensity.current) { boxSizeDp.toPx() }

    var activeShape by remember { mutableStateOf(initialShape) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Dimensions accounting for 90 or 270 deg rotation
    val isRotatedSideways = (rotationDegrees.toInt() % 180 != 0)
    val effectiveW = if (isRotatedSideways) sourceBitmap.height.toFloat() else sourceBitmap.width.toFloat()
    val effectiveH = if (isRotatedSideways) sourceBitmap.width.toFloat() else sourceBitmap.height.toFloat()

    val baseScale = maxOf(boxPx / effectiveW, boxPx / effectiveH)
    val totalScale = baseScale * zoomFactor

    val scaledW = effectiveW * totalScale
    val scaledH = effectiveH * totalScale
    val maxPanX = maxOf(0f, (scaledW - boxPx) / 2f)
    val maxPanY = maxOf(0f, (scaledH - boxPx) / 2f)

    fun performCrop() {
        val outputSize = 512
        val cropped = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val ratio = outputSize / boxPx

        val matrix = Matrix()
        matrix.postTranslate(-sourceBitmap.width / 2f, -sourceBitmap.height / 2f)
        matrix.postRotate(rotationDegrees)
        matrix.postScale(totalScale * ratio, totalScale * ratio)
        matrix.postTranslate(
            outputSize / 2f + panOffset.x.coerceIn(-maxPanX, maxPanX) * ratio,
            outputSize / 2f + panOffset.y.coerceIn(-maxPanY, maxPanY) * ratio,
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, paint)
        onCropped(cropped, activeShape)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090B11),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- 1. Top App Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.settings_crop_cancel),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_crop_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.settings_crop_dialog_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }

                Button(
                    onClick = { performCrop() },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            // --- 2. Interactive Crop Viewport (Centered, flexible) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(boxSizeDp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .clipToBounds()
                            .pointerInput(rotationDegrees, zoomFactor) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (zoomFactor * zoom).coerceIn(1f, 4f)
                                    zoomFactor = newZoom

                                    val newScale = baseScale * newZoom
                                    val newScaledW = effectiveW * newScale
                                    val newScaledH = effectiveH * newScale
                                    val newMaxPanX = maxOf(0f, (newScaledW - boxPx) / 2f)
                                    val newMaxPanY = maxOf(0f, (newScaledH - boxPx) / 2f)

                                    panOffset = Offset(
                                        (panOffset.x + pan.x).coerceIn(-newMaxPanX, newMaxPanX),
                                        (panOffset.y + pan.y).coerceIn(-newMaxPanY, newMaxPanY),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val matrix = Matrix()
                            matrix.postTranslate(-sourceBitmap.width / 2f, -sourceBitmap.height / 2f)
                            matrix.postRotate(rotationDegrees)
                            matrix.postScale(totalScale, totalScale)
                            matrix.postTranslate(
                                boxPx / 2f + panOffset.x.coerceIn(-maxPanX, maxPanX),
                                boxPx / 2f + panOffset.y.coerceIn(-maxPanY, maxPanY),
                            )

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            drawContext.canvas.nativeCanvas.drawBitmap(sourceBitmap, matrix, paint)

                            // Rule of thirds lines
                            val third = size.width / 3f
                            drawLine(
                                color = Color.White.copy(alpha = 0.22f),
                                start = Offset(third, 0f),
                                end = Offset(third, size.height),
                                strokeWidth = 1f,
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.22f),
                                start = Offset(third * 2f, 0f),
                                end = Offset(third * 2f, size.height),
                                strokeWidth = 1f,
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.22f),
                                start = Offset(0f, third),
                                end = Offset(size.width, third),
                                strokeWidth = 1f,
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.22f),
                                start = Offset(0f, third * 2f),
                                end = Offset(size.width, third * 2f),
                                strokeWidth = 1f,
                            )

                            // Guide boundary matching activeShape
                            when (activeShape) {
                                LogoShape.ROUND -> {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.55f),
                                        radius = size.width / 2f - 2f,
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }
                                LogoShape.SIDE_ROUND -> {
                                    val r = size.width * 0.22f
                                    val path = Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                2f,
                                                2f,
                                                size.width - 2f,
                                                size.height - 2f,
                                                CornerRadius(r, r),
                                            )
                                        )
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White.copy(alpha = 0.55f),
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }
                                LogoShape.SQUARE -> {
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.55f),
                                        topLeft = Offset(2f, 2f),
                                        size = androidx.compose.ui.geometry.Size(size.width - 4f, size.height - 4f),
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Drag to reposition • Pinch to scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                    )
                }
            }

            // --- 3. Bottom Controls Panel Card ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF131622),
                border = BorderStroke(1.dp, Color(0xFF222738)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Shape Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Shape",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        SingleChoiceSegmentedButtonRow {
                            val shapes = LogoShape.entries
                            shapes.forEachIndexed { index, itemShape ->
                                SegmentedButton(
                                    selected = itemShape == activeShape,
                                    onClick = { activeShape = itemShape },
                                    shape = SegmentedButtonDefaults.itemShape(index, shapes.size),
                                    label = {
                                        Text(
                                            text = when (itemShape) {
                                                LogoShape.ROUND -> "Round"
                                                LogoShape.SIDE_ROUND -> "Side Round"
                                                LogoShape.SQUARE -> "Square"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Zoom Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                        )
                        Slider(
                            value = zoomFactor,
                            onValueChange = { newZoom ->
                                zoomFactor = newZoom
                                val newScale = baseScale * newZoom
                                val newScaledW = effectiveW * newScale
                                val newScaledH = effectiveH * newScale
                                val newMaxPanX = maxOf(0f, (newScaledW - boxPx) / 2f)
                                val newMaxPanY = maxOf(0f, (newScaledH - boxPx) / 2f)
                                panOffset = Offset(
                                    panOffset.x.coerceIn(-newMaxPanX, newMaxPanX),
                                    panOffset.y.coerceIn(-newMaxPanY, newMaxPanY),
                                )
                            },
                            valueRange = 1f..4f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(zoomFactor * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Tool Buttons Row (Rotate, Reset)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90f) % 360f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(AppIcons.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rotate 90°", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = {
                                zoomFactor = 1f
                                panOffset = Offset.Zero
                                rotationDegrees = 0f
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset View", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Primary Action Buttons (Cancel & Save & Apply)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_crop_cancel),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }

                        Button(
                            onClick = { performCrop() },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_crop_save),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
