package com.budora.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.core.content.ContextCompat
import com.budora.data.BudoraRepository
import com.budora.data.NowPlayingRepository
import com.budora.data.SettingsStore
import com.budora.service.BondReceiver
import com.budora.service.BudoraMediaService
import com.budora.service.BudoraService
import com.budora.ui.theme.BudoraTheme
import com.budora.ui.theme.resolve

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        if (granted) {
            startBudoraServiceSafely()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestMissingPermissions()
        startBudoraServiceSafely()

        setContent {
            val settings by SettingsStore.settings.collectAsState()
            val isDark = settings.darkMode.resolve()

            // enableEdgeToEdge() above read the *system* dark setting for the
            // status-bar icon colour. When the user overrides it in Settings,
            // re-apply so the icons keep contrasting with the app's background.
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM) { isDark },
                )
            }

            BudoraTheme(colorTheme = settings.colorTheme, darkTheme = isDark) {
                // Two screens, one Activity; a navigation library would be
                // ceremony for a readout and its options page.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = settings,
                        onChange = SettingsStore::update,
                        onBack = { showSettings = false },
                    )
                } else {
                    val connection by BudoraRepository.state.collectAsState()
                    val deviceName by BudoraRepository.deviceName.collectAsState()
                    BudoraScreen(
                        connection = connection,
                        onOpenSettings = { showSettings = true },
                        deviceName = deviceName,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        val cn = ComponentName(this, BudoraMediaService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val enabled = flat.contains(cn.flattenToString()) || flat.contains(packageName)
        NowPlayingRepository.setPermission(enabled)
        if (enabled) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(cn)
                BudoraMediaService.instance?.refreshActiveSession()
            } catch (_: Exception) {}
        }
    }

    private fun startBudoraServiceSafely() {
        try {
            BondReceiver.startIfAlreadyConnected(this)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                BudoraService.start(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start BudoraService safely", e)
        }
    }

    /**
     * Ask only for what is not already granted.
     *
     * Note there is no ACCESS_FINE_LOCATION here: BLUETOOTH_SCAN carries
     * neverForLocation in the manifest, which removes the location requirement
     * and the prompt that goes with it.
     */
    private fun requestMissingPermissions() {
        val required = buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private companion object {
        // The defaults enableEdgeToEdge() uses for a three-button navigation bar.
        val NAV_BAR_LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val NAV_BAR_DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
