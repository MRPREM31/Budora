package com.budora.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budora.BuildConfig
import com.budora.R
import com.budora.data.AppLogoManager
import com.budora.data.LogoShape
import com.budora.data.AppSettings
import com.budora.data.ColorTheme
import com.budora.data.DarkMode
import com.budora.data.PopupPosition
import com.budora.ui.components.LogoCropDialog
import com.budora.ui.overlay.ConnectPopupHost
import com.budora.ui.overlay.OverlayPermission
import com.budora.ble.BudoraModel
import com.budora.ble.BudoraState
import com.budora.data.LastCaseReading
import com.budora.ui.theme.Palettes

/**
 * Options. Same typographic register as the readout: eyebrow labels with a
 * small icon, no cards, the controls are the only chrome. Four sections
 * because there are four concerns.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val logoVersion by AppLogoManager.logoVersion.collectAsState()
    val currentLogoBitmap = remember(logoVersion) {
        AppLogoManager.getLogoBitmap(context)
    }
    val hasCustom = remember(logoVersion) {
        AppLogoManager.hasCustomLogo(context)
    }
    val currentShape = remember(logoVersion) {
        AppLogoManager.getLogoShape(context)
    }

    var rawBitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val loaded = AppLogoManager.loadBitmapFromUri(context, uri)
            if (loaded != null) {
                rawBitmapToCrop = loaded
            } else {
                Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionLabel(AppIcons.Photo, stringResource(R.string.settings_section_branding))
            AppLogoSection(
                currentLogo = currentLogoBitmap,
                hasCustom = hasCustom,
                shape = currentShape,
                onShapeChange = { newShape ->
                    AppLogoManager.setLogoShape(context, newShape)
                },
                onPickPhoto = { photoPickerLauncher.launch("image/*") },
                onResetDefault = {
                    AppLogoManager.resetToDefault(context)
                    Toast.makeText(context, "Logo reset to default", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel(AppIcons.Palette, stringResource(R.string.settings_section_appearance))
            FieldLabel(stringResource(R.string.settings_theme))
            ThemePicker(
                selected = settings.colorTheme,
                onSelect = { theme -> onChange { it.copy(colorTheme = theme) } },
            )
            Spacer(Modifier.height(16.dp))
            FieldLabel(stringResource(R.string.settings_dark_mode))
            DarkModePicker(
                selected = settings.darkMode,
                onSelect = { mode -> onChange { it.copy(darkMode = mode) } },
            )

            Spacer(Modifier.height(32.dp))

            SectionLabel(Icons.Outlined.Notifications, stringResource(R.string.settings_section_popup))
            SwitchRow(
                title = stringResource(R.string.settings_connect_popup),
                summary = stringResource(R.string.settings_connect_popup_summary),
                checked = settings.showConnectPopup,
                onCheckedChange = { on -> onChange { it.copy(showConnectPopup = on) } },
            )
            // Only while the popup is actually wanted: the grant is useless
            // otherwise, and asking for it unprompted is the kind of thing
            // that makes people uninstall an app.
            if (settings.showConnectPopup) {
                OverlayPermissionRow()
                FieldLabel(stringResource(R.string.settings_popup_position))
                PopupPositionPicker(
                    selected = settings.popupPosition,
                    onSelect = { position -> onChange { it.copy(popupPosition = position) } },
                )
            }
            if (BuildConfig.DEBUG) PreviewPopupRow(settings)
            SwitchRow(
                title = stringResource(R.string.settings_auto_dismiss),
                summary = stringResource(R.string.settings_auto_dismiss_summary),
                checked = settings.autoDismissPopup,
                onCheckedChange = { on -> onChange { it.copy(autoDismissPopup = on) } },
            )
            TimeoutChoiceRow(
                enabled = settings.autoDismissPopup,
                selectedSeconds = settings.popupTimeoutSeconds,
                onSelect = { s -> onChange { it.copy(popupTimeoutSeconds = s) } },
            )

            Spacer(Modifier.height(32.dp))

            SectionLabel(AppIcons.MusicNote, stringResource(R.string.settings_section_audio))
            SwitchRow(
                title = stringResource(R.string.settings_resume_music),
                summary = stringResource(R.string.settings_resume_music_summary),
                checked = settings.resumeMusicOnConnect,
                onCheckedChange = { on -> onChange { it.copy(resumeMusicOnConnect = on) } },
            )

            Spacer(Modifier.height(32.dp))

            SectionLabel(AppIcons.Widgets, stringResource(R.string.settings_section_widget))
            HomeScreenWidgetRows()

            Spacer(Modifier.height(32.dp))

            SectionLabel(AppIcons.TouchApp, stringResource(R.string.settings_section_gestures))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.settings_gestures_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_gestures_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
            VersionFooter()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (rawBitmapToCrop != null) {
        androidx.activity.compose.BackHandler { rawBitmapToCrop = null }
        LogoCropDialog(
            sourceBitmap = rawBitmapToCrop!!,
            initialShape = currentShape,
            onDismiss = { rawBitmapToCrop = null },
            onCropped = { cropped, chosenShape ->
                AppLogoManager.setLogoShape(context, chosenShape)
                AppLogoManager.saveCustomLogo(context, cropped)
                rawBitmapToCrop = null
                Toast.makeText(context, "Budora logo updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
        )
    }
}

/**
 * Which build is installed, so a bug report can name it. Debug builds say so:
 * they share the version name with the release but not its signature, and
 * one cannot be installed over the other.
 */
@Composable
private fun VersionFooter() {
    val version = if (BuildConfig.DEBUG) {
        stringResource(R.string.settings_version_debug, BuildConfig.VERSION_NAME)
    } else {
        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
    }
    Text(
        text = version,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_creator),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * One swatch per palette, showing its light and dark accent.
 *
 * Every palette now has fixed colours of its own -- including Default, which
 * is the neutral colourway rather than "whatever the wallpaper says" -- so
 * they all render the same way.
 */
@Composable
private fun ThemePicker(selected: ColorTheme, onSelect: (ColorTheme) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        ColorTheme.entries.forEach { theme ->
            ThemeSwatch(
                theme = theme,
                isSelected = theme == selected,
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ColorTheme, isSelected: Boolean, onClick: () -> Unit) {
    val name = stringResource(theme.nameRes())
    val colors = Palettes.swatch(theme)
    val brush = Brush.linearGradient(colors)
    val ring = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .border(3.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(brush),
            Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Prompts for the overlay grant, and disappears once it is held.
 *
 * Re-checked on every resume rather than once: the grant is given on a
 * Settings page in another task, so the only reliable moment to learn it
 * changed is coming back to this screen.
 */
@Composable
private fun OverlayPermissionRow() {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(OverlayPermission.isGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isGranted = OverlayPermission.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isGranted) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_overlay_permission),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_overlay_permission_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = { context.startActivity(OverlayPermission.requestIntent(context)) }) {
            Text(stringResource(R.string.settings_overlay_grant))
        }
    }
}

/**
 * Debug-only: renders the popup on demand with sample levels.
 *
 * Shown with the *live* settings object, so the preview is exactly what a real
 * connect would draw -- theme, position and dismiss timeout included. Tweaking
 * a colour and seeing it otherwise costs a disconnect/reconnect of the pods.
 */
@Composable
private fun PreviewPopupRow(settings: AppSettings) {
    val context = LocalContext.current
    val host = remember(context) { ConnectPopupHost(context.applicationContext) }
    // The window must come down with the screen that opened it; a preview left
    // attached would outlive Settings and float over everything.
    DisposableEffect(host) { onDispose { host.destroy() } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_preview_popup),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_preview_popup_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextButton(
            onClick = {
                if (OverlayPermission.isGranted(context)) {
                    host.show(PREVIEW_STATE, PREVIEW_CASE, settings)
                } else {
                    context.startActivity(OverlayPermission.requestIntent(context))
                }
            },
        ) {
            Text(stringResource(R.string.settings_preview_show))
        }
    }
}

/** Levels chosen to span all three battery colour bands at once. */
private val PREVIEW_STATE = BudoraState(
    model = BudoraModel.AIRPODS_GEN_2,
    leftBattery = 80,
    rightBattery = 35,
    caseBattery = null,
    isLeftCharging = false,
    isRightCharging = true,
    isCaseCharging = false,
    isLidOpen = false,
    lidOpenCounter = 0,
    rawLeftInEar = true,
    rawRightInEar = true,
)

/** Null case level above, so this also exercises the remembered path. */
private val PREVIEW_CASE = LastCaseReading(level = 15, isCharging = false, seenAtMs = 0L)

@Composable
private fun PopupPositionPicker(selected: PopupPosition, onSelect: (PopupPosition) -> Unit) {
    val positions = PopupPosition.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        positions.forEachIndexed { index, position ->
            SegmentedButton(
                selected = position == selected,
                onClick = { onSelect(position) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = positions.size),
                label = { Text(stringResource(position.nameRes())) },
            )
        }
    }
}

private fun PopupPosition.nameRes(): Int = when (this) {
    PopupPosition.TOP -> R.string.popup_position_top
    PopupPosition.BOTTOM -> R.string.popup_position_bottom
}

@Composable
private fun HomeScreenWidgetRows() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_add_widget),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_add_widget_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Button(
                onClick = {
                    val appWidgetManager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                    if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                        val provider = android.content.ComponentName(
                            context,
                            com.budora.widget.BudoraWidgetProvider::class.java
                        )
                        appWidgetManager.requestPinAppWidget(provider, null, null)
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_add_shortcut),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_add_shortcut_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Button(
                onClick = {
                    val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                        val intent = android.content.Intent(
                            context,
                            com.budora.ui.BudoraQuickPopupActivity::class.java
                        ).apply {
                            action = android.content.Intent.ACTION_VIEW
                        }
                        val activeLogo = AppLogoManager.getLogoBitmap(context)
                        val info = android.content.pm.ShortcutInfo.Builder(context, "budora_quick_popup")
                            .setShortLabel("Budora")
                            .setLongLabel("Budora Battery Popup")
                            .setIcon(android.graphics.drawable.Icon.createWithBitmap(activeLogo))
                            .setIntent(intent)
                            .build()
                        shortcutManager.requestPinShortcut(info, null)
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun DarkModePicker(selected: DarkMode, onSelect: (DarkMode) -> Unit) {
    val modes = DarkMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        modes.forEachIndexed { index, mode ->
            val isSelected = mode == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = isSelected) {
                        Icon(
                            mode.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
                label = { Text(stringResource(mode.nameRes())) },
            )
        }
    }
}

private fun ColorTheme.nameRes(): Int = when (this) {
    ColorTheme.SYSTEM -> R.string.theme_system
    ColorTheme.OCEAN -> R.string.theme_ocean
    ColorTheme.SUNSET -> R.string.theme_sunset
    ColorTheme.FOREST -> R.string.theme_forest
    ColorTheme.GRAPE -> R.string.theme_grape
}

private fun DarkMode.nameRes(): Int = when (this) {
    DarkMode.SYSTEM -> R.string.dark_mode_system
    DarkMode.LIGHT -> R.string.dark_mode_light
    DarkMode.DARK -> R.string.dark_mode_dark
}

private fun DarkMode.icon(): ImageVector = when (this) {
    DarkMode.SYSTEM -> AppIcons.BrightnessAuto
    DarkMode.LIGHT -> AppIcons.Sun
    DarkMode.DARK -> AppIcons.Moon
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        // The row is the toggle; the switch is its indicator.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun TimeoutChoiceRow(enabled: Boolean, selectedSeconds: Int, onSelect: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_popup_timeout),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        AppSettings.POPUP_TIMEOUT_CHOICES_SECONDS.forEach { seconds ->
            FilterChip(
                selected = seconds == selectedSeconds,
                enabled = enabled,
                onClick = { onSelect(seconds) },
                label = { Text(stringResource(R.string.settings_seconds, seconds)) },
            )
        }
    }
}

private val SWATCH_SIZE = 52.dp

@Composable
private fun AppLogoSection(
    currentLogo: Bitmap,
    hasCustom: Boolean,
    shape: LogoShape,
    onShapeChange: (LogoShape) -> Unit,
    onPickPhoto: () -> Unit,
    onResetDefault: () -> Unit,
) {
    val context = LocalContext.current
    val avatarShape = when (shape) {
        LogoShape.ROUND -> CircleShape
        LogoShape.SIDE_ROUND -> RoundedCornerShape(18.dp)
        LogoShape.SQUARE -> RoundedCornerShape(4.dp)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Logo avatar with dynamic shape
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(avatarShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, avatarShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = currentLogo.asImageBitmap(),
                        contentDescription = stringResource(R.string.settings_logo_title),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(avatarShape),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            AppIcons.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_app_name_fixed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = stringResource(
                                if (hasCustom) R.string.settings_logo_badge_custom
                                else R.string.settings_logo_badge_default
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCustom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Shape Selector (Round / Side Round / Square)
            Text(
                text = stringResource(R.string.settings_logo_shape_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val shapes = LogoShape.entries
                shapes.forEachIndexed { index, itemShape ->
                    val isSelected = itemShape == shape
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onShapeChange(itemShape) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = shapes.size),
                        label = {
                            Text(
                                text = when (itemShape) {
                                    LogoShape.ROUND -> stringResource(R.string.logo_shape_round)
                                    LogoShape.SIDE_ROUND -> stringResource(R.string.logo_shape_side_round)
                                    LogoShape.SQUARE -> stringResource(R.string.logo_shape_square)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.settings_logo_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPickPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(AppIcons.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_upload_logo))
                }

                if (hasCustom) {
                    OutlinedButton(
                        onClick = onResetDefault,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_reset_logo))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                        val intent = android.content.Intent(context, com.budora.ui.MainActivity::class.java).apply {
                            action = android.content.Intent.ACTION_MAIN
                        }
                        val styledIcon = AppLogoManager.getStyledLogoBitmap(context, shape)
                        val info = android.content.pm.ShortcutInfo.Builder(context, "budora_app_icon")
                            .setShortLabel("Budora")
                            .setLongLabel("Budora")
                            .setIcon(android.graphics.drawable.Icon.createWithBitmap(styledIcon))
                            .setIntent(intent)
                            .build()
                        shortcutManager.requestPinShortcut(info, null)
                        Toast.makeText(context, "App icon pinned to Home screen!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Home screen shortcuts not supported by current launcher", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_add_app_shortcut))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pins a 1x1 Budora launcher icon with your custom photo and shape to your Home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
