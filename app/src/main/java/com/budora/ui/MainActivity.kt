package com.budora.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.core.content.ContextCompat
import com.budora.ble.BudoraModel
import com.budora.ble.BudoraState
import com.budora.data.BudoraConnection
import com.budora.data.BudoraRepository
import com.budora.data.LastCaseReading
import com.budora.data.NowPlayingRepository
import com.budora.data.PopupPosition
import com.budora.data.SettingsStore
import com.budora.service.BondReceiver
import com.budora.service.BudoraMediaService
import com.budora.service.BudoraService
import com.budora.ui.overlay.ConnectPopupCard
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

    private val showSettingsState = mutableStateOf(false)
    private val showPopupState = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showSettingsState.value = intent.getBooleanExtra("open_settings", false)
        showPopupState.value = intent.getBooleanExtra("open_popup", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        showSettingsState.value = intent?.getBooleanExtra("open_settings", false) == true
        showPopupState.value = intent?.getBooleanExtra("open_popup", false) == true

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
                var showSettings by showSettingsState
                var showPopup by showPopupState

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
                    Box(Modifier.fillMaxSize()) {
                        BudoraScreen(
                            connection = connection,
                            onOpenSettings = { showSettings = true },
                            deviceName = deviceName,
                        )
                        if (showPopup) {
                            val liveConn = connection as? BudoraConnection.Live
                            val pState = liveConn?.state ?: BudoraState(
                                model = BudoraModel.AIRPODS_PRO,
                                leftBattery = 85,
                                rightBattery = 85,
                                caseBattery = 70,
                                isLeftCharging = false,
                                isRightCharging = false,
                                isCaseCharging = false,
                                isLidOpen = true,
                                lidOpenCounter = 1,
                                rawLeftInEar = false,
                                rawRightInEar = false,
                            )
                            val pVisible = remember { MutableTransitionState(true) }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 32.dp),
                                contentAlignment = if (settings.popupPosition == PopupPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
                            ) {
                                ConnectPopupCard(
                                    state = pState,
                                    lastCase = liveConn?.lastCase ?: LastCaseReading(70, false, System.currentTimeMillis()),
                                    position = settings.popupPosition,
                                    visibleState = pVisible,
                                    onDismiss = { showPopup = false },
                                    deviceName = deviceName ?: "CMF Buds 2a"
                                )
                            }
                        }
                    }
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
