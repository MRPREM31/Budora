package com.budora.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import java.util.UUID
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.budora.R
import com.budora.ble.BudoraModel
import com.budora.ble.BudoraScanner
import com.budora.ble.BudoraState
import com.budora.ble.BudoraTracker
import com.budora.data.CaseMemory
import com.budora.data.LastCaseReading
import com.budora.data.BudoraConnection
import com.budora.data.BudoraRepository
import com.budora.data.SettingsStore
import com.budora.ui.overlay.ConnectPopupHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn

/**
 * Foreground service that scans while the AirPods are connected.
 *
 * Lifecycle is driven entirely by [BondReceiver]: started on ACL connect,
 * stopped on ACL disconnect. It deliberately does not run while the pods are
 * away -- a permanently resident scanner is the main way apps like this drain a
 * battery, and there is nothing to report when the pods are in a bag.
 *
 * Android 14+ requires the connectedDevice foreground-service type (declared in
 * the manifest) and BLUETOOTH_CONNECT held at the moment startForeground runs,
 * or the platform throws.
 */
class BudoraService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracker = BudoraTracker()
    private lateinit var scanner: BudoraScanner
    private lateinit var caseMemory: CaseMemory
    private lateinit var popupHost: ConnectPopupHost

    private var scanJob: Job? = null
    private var batteryJob: Job? = null
    private var wasLidOpen = false

    private var alertedThresholdLeft: Int? = null
    private var alertedThresholdRight: Int? = null
    private var alertedThresholdCase: Int? = null
    private var lastAlertTimeLeft = 0L
    private var lastAlertTimeRight = 0L
    private var lastAlertTimeCase = 0L

    /**
     * Whether the connect popup has already been shown for this connection.
     *
     * The service is started on the ACL connect edge and destroyed on
     * disconnect, so an instance field is exactly one connection's worth of
     * memory -- no reset logic, and reconnecting shows the popup again.
     */
    private var hasShownConnectPopup = false

    /**
     * The newest reading, kept so a popup deferred by the lock screen shows
     * current numbers when it finally appears rather than ones from connect.
     */
    @Volatile private var latestState: BudoraState? = null
    @Volatile private var latestCase: LastCaseReading? = null

    /**
     * Registered only while a popup is waiting for the user to unlock, and
     * only for the life of this service, so it never outlives a connection.
     */
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        scanner = BudoraScanner(this)
        caseMemory = CaseMemory.persistent(this)
        popupHost = ConnectPopupHost(this)
        BudoraNotifications.ensureChannels(this)
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.device.action.METADATA_CHANGED")
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        }
        ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()

        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "Missing Bluetooth permissions; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        BudoraRepository.onSearching()
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            startScanning()
        }
        startBatteryPolling()

        // Not sticky: restarting without a live ACL connection would scan for
        // pods that are not there. BondReceiver restarts us when they return.
        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val notification = BudoraNotifications.ongoing(this, null)
        try {
            ServiceCompat.startForeground(
                this,
                BudoraNotifications.ONGOING_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            // Covers ForegroundServiceStartNotAllowedException (API 31+) and
            // the API 34 type/permission mismatch. Neither is recoverable here.
            Log.e(TAG, "startForeground rejected", e)
            stopSelf()
        }
    }

    /**
     * BLUETOOTH_SCAN is verified in onStartCommand, but can be revoked between
     * that check and the scan actually starting, so the SecurityException is
     * handled rather than assumed away.
     */
    @SuppressLint("MissingPermission")
    private fun startScanning(targetAddress: String? = null) {
        if (scanJob?.isActive == true) return

        scanJob = scanner.advertisements(ScanSettings.SCAN_MODE_LOW_LATENCY, targetAddress)
            .onEach { advertisement ->
                Log.i(TAG, "BLE advertisement: ${advertisement.address} state=${advertisement.state}")
                val rawState = tracker.onAdvertisement(
                    advertisement.address,
                    advertisement.rssi,
                    advertisement.state,
                ) ?: advertisement.state

                val isLeftChg = rawState.isLeftCharging
                val isRightChg = rawState.isRightCharging
                val isSingle = rawState.model == BudoraModel.AIRPODS_MAX || rawState.isSingleDevice
                val leftInCase = !isSingle && (
                    (isLeftChg && !isRightChg) ||
                    (rawState.leftBattery == null && rawState.rightBattery != null)
                )
                val rightInCase = !isSingle && (
                    (isRightChg && !isLeftChg) ||
                    (rawState.rightBattery == null && rawState.leftBattery != null)
                )
                val state = rawState.copy(
                    isSingleDevice = isSingle,
                    isLeftInCase = leftInCase,
                    isRightInCase = rightInCase,
                )

                val lastCase = if (state.caseBattery != null && state.caseBattery > 0) {
                    caseMemory.update(state.caseBattery, state.isCaseCharging)
                } else {
                    latestCase ?: caseMemory.last
                }
                latestState = state
                latestCase = lastCase
                BudoraRepository.onState(state, lastCase)
                showConnectPopupOnce(state, lastCase)
                notifyLidTransition(state, lastCase)
                updateOngoing(state, lastCase)
            }
            .catch { cause ->
                Log.e(TAG, "Scan flow failed", cause)
                if (cause is SecurityException) stopSelf()
            }
            .launchIn(scope)
    }

    /**
     * Slide the battery card in the first time this connection yields a real
     * reading.
     *
     * Deliberately keyed on the first *decoded beacon* rather than on the ACL
     * connect edge: at connect time there are no numbers yet, and a card full
     * of dashes that fills in a second later is worse than one that arrives
     * already correct.
     */
    private fun showConnectPopupOnce(state: BudoraState, lastCase: LastCaseReading?) {
        if (hasShownConnectPopup) return
        val settings = SettingsStore.settings.value
        if (!settings.showConnectPopup) return
        hasShownConnectPopup = true
        if (isUserLookingAtScreen()) {
            popupHost.show(state, lastCase, settings)
        } else {
            deferPopupUntilUnlocked()
        }
    }

    /**
     * Android never draws overlay windows above the lock screen, and a card
     * shown to a dark screen would time out unseen. Either way the popup is
     * held until the user is actually looking at an unlocked phone.
     */
    private fun isUserLookingAtScreen(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive == true &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    /**
     * Wait for the unlock, then show the card with the newest reading.
     *
     * USER_PRESENT fires after a real unlock; SCREEN_ON covers phones with no
     * secure lock (or Smart Lock), where waking the screen is the unlock. Both
     * are pushed by the system, so waiting costs nothing.
     */
    private fun deferPopupUntilUnlocked() {
        if (unlockReceiver != null) return
        Log.d(TAG, "Screen locked or off; deferring popup until unlock")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isUserLookingAtScreen()) return
                val state = latestState ?: return
                unregisterUnlockReceiver()
                val settings = SettingsStore.settings.value
                if (settings.showConnectPopup) {
                    Log.d(TAG, "Unlocked; showing deferred popup")
                    popupHost.show(state, latestCase, settings)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // EXPORTED, for the same reason as BondReceiver: USER_PRESENT is sent
        // by SystemUI (an app uid, not the system uid), so a NOT_EXPORTED
        // receiver gets SCREEN_ON but never the unlock. Verified on-device.
        // Both actions are protected broadcasts, so nothing else can spoof them.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        unlockReceiver = receiver
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
    }

    /**
     * Fire the popup on the shut -> open edge only, not on every packet.
     *
     * "Open" is only meaningful while a pod is in the case: with both pods out,
     * the lid byte reads as open on every frame (captured on hardware: byte 8
     * is 0x01 in that state), which used to fire a "case opened" popup the
     * moment the pods were first seen in your ears. A pod in the case is
     * exactly when the case level is known, so that is the gate.
     */
    private fun notifyLidTransition(state: BudoraState, lastCase: LastCaseReading?) {
        val isLidOpen = state.isLidOpen && state.caseBattery != null
        if (isLidOpen && !wasLidOpen && hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            val settings = SettingsStore.settings.value
            getSystemService(NotificationManager::class.java).notify(
                BudoraNotifications.LID_NOTIFICATION_ID,
                BudoraNotifications.lidOpened(
                    this,
                    state,
                    lastCase,
                    timeoutMs = settings.popupTimeoutMs.takeIf { settings.autoDismissPopup },
                ),
            )
        }
        wasLidOpen = isLidOpen
    }

    private fun updateOngoing(state: BudoraState, lastCase: LastCaseReading?) {
        com.budora.widget.BudoraWidgetProvider.updateAllWidgets(this)
        checkLowBatteryAlerts(state, lastCase)
        if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        getSystemService(NotificationManager::class.java).notify(
            BudoraNotifications.ONGOING_NOTIFICATION_ID,
            BudoraNotifications.ongoing(this, state, lastCase),
        )
    }

    private fun checkLowBatteryAlerts(state: BudoraState, lastCase: LastCaseReading?) {
        if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val name = BudoraRepository.deviceName.value ?: getString(R.string.app_name)
        val title = getString(R.string.low_battery_title, name)
        val now = System.currentTimeMillis()
        val cooldownMs = 15 * 60 * 1000L // 15 min cooldown

        // Check Left Earbud
        val left = state.leftBattery
        val isLeftCharging = state.isLeftCharging
        if (isLeftCharging || left == null || left <= 0 || left > 22) {
            if (alertedThresholdLeft != null && (isLeftCharging || (left != null && left > 22))) {
                alertedThresholdLeft = null
                notificationManager.cancel(BudoraNotifications.LOW_BATTERY_LEFT_NOTIFICATION_ID)
            }
        } else {
            if (left <= 10) {
                if (alertedThresholdLeft != 10 || (now - lastAlertTimeLeft > cooldownMs)) {
                    alertedThresholdLeft = 10
                    lastAlertTimeLeft = now
                    val text = getString(R.string.low_battery_left_critical, left)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_LEFT_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            } else if (left <= 20) {
                if (alertedThresholdLeft == null || (alertedThresholdLeft == 20 && now - lastAlertTimeLeft > cooldownMs)) {
                    alertedThresholdLeft = 20
                    lastAlertTimeLeft = now
                    val text = getString(R.string.low_battery_left, left)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_LEFT_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            }
        }

        // Check Right Earbud
        val right = state.rightBattery
        val isRightCharging = state.isRightCharging
        if (isRightCharging || right == null || right <= 0 || right > 22) {
            if (alertedThresholdRight != null && (isRightCharging || (right != null && right > 22))) {
                alertedThresholdRight = null
                notificationManager.cancel(BudoraNotifications.LOW_BATTERY_RIGHT_NOTIFICATION_ID)
            }
        } else {
            if (right <= 10) {
                if (alertedThresholdRight != 10 || (now - lastAlertTimeRight > cooldownMs)) {
                    alertedThresholdRight = 10
                    lastAlertTimeRight = now
                    val text = getString(R.string.low_battery_right_critical, right)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_RIGHT_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            } else if (right <= 20) {
                if (alertedThresholdRight == null || (alertedThresholdRight == 20 && now - lastAlertTimeRight > cooldownMs)) {
                    alertedThresholdRight = 20
                    lastAlertTimeRight = now
                    val text = getString(R.string.low_battery_right, right)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_RIGHT_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            }
        }

        // Check Case
        val case = state.caseBattery ?: lastCase?.level
        val isCaseCharging = state.isCaseCharging || (lastCase?.isCharging == true)
        if (isCaseCharging || case == null || case <= 0 || case > 22) {
            if (alertedThresholdCase != null && (isCaseCharging || (case != null && case > 22))) {
                alertedThresholdCase = null
                notificationManager.cancel(BudoraNotifications.LOW_BATTERY_CASE_NOTIFICATION_ID)
            }
        } else {
            if (case <= 10) {
                if (alertedThresholdCase != 10 || (now - lastAlertTimeCase > cooldownMs)) {
                    alertedThresholdCase = 10
                    lastAlertTimeCase = now
                    val text = getString(R.string.low_battery_case_critical, case)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_CASE_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            } else if (case <= 20) {
                if (alertedThresholdCase == null || (alertedThresholdCase == 20 && now - lastAlertTimeCase > cooldownMs)) {
                    alertedThresholdCase = 20
                    lastAlertTimeCase = now
                    val text = getString(R.string.low_battery_case, case)
                    notificationManager.notify(
                        BudoraNotifications.LOW_BATTERY_CASE_NOTIFICATION_ID,
                        BudoraNotifications.lowBatteryAlert(this, title, text),
                    )
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startBatteryPolling() {
        if (batteryJob?.isActive == true) return
        batteryJob = scope.launch {
            while (isActive) {
                pollConnectedDeviceBattery()
                delay(1500L)
            }
        }
    }

    private fun pollConnectedDeviceBattery() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java) ?: return
        val adapter = bluetoothManager.adapter ?: return

        // Verify active A2DP or Headset profile connection
        val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        if (!a2dp && !headset) {
            if (BudoraRepository.state.value !is BudoraConnection.Disconnected) {
                Log.d(TAG, "No audio earbuds connected; setting disconnected")
                BudoraRepository.onDisconnected()
                com.budora.widget.BudoraWidgetProvider.updateAllWidgets(this)
            }
            return
        }

        val devices = try {
            adapter.bondedDevices.orEmpty()
        } catch (e: SecurityException) {
            emptySet()
        }

        var foundAudio = false
        for (device in devices) {
            if (!isAudioEarbud(device)) continue
            if (!device.isConnectedCompat()) continue

            foundAudio = true
            connectGattIfNeeded(device)

            val name = (device.alias ?: device.name)?.takeIf { it.isNotBlank() }
            if (name != null) {
                BudoraRepository.onDeviceName(name)
            }

            val state = readDeviceBattery(device) ?: continue
            latestState = state
            val lastCase = latestCase ?: caseMemory.last
            latestCase = lastCase
            BudoraRepository.onState(state, lastCase)
            showConnectPopupOnce(state, lastCase)
            updateOngoing(state, lastCase)
            break
        }

        if (!foundAudio && BudoraRepository.state.value !is BudoraConnection.Disconnected) {
            BudoraRepository.onDisconnected()
            com.budora.widget.BudoraWidgetProvider.updateAllWidgets(this)
        }
    }

    private fun isAudioEarbud(device: BluetoothDevice): Boolean = try {
        val n = (device.alias ?: device.name).orEmpty().lowercase()
        val isEarbudName = n.contains("buds") ||
            n.contains("airpod") ||
            n.contains("ear") ||
            n.contains("headset") ||
            n.contains("headphone") ||
            n.contains("cmf") ||
            n.contains("audio") ||
            n.contains("sound") ||
            n.contains("boat") ||
            n.contains("oppo") ||
            n.contains("oneplus") ||
            n.contains("realme")
        val isAudioClass = device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
        isEarbudName || isAudioClass
    } catch (e: SecurityException) {
        false
    }

    private fun BluetoothDevice.isConnectedCompat(): Boolean = try {
        val method = javaClass.getMethod("isConnected")
        method.invoke(this) as? Boolean ?: true
    } catch (e: Exception) {
        true
    }

    private var activeGatt: BluetoothGatt? = null
    private var gattTargetAddress: String? = null

    @SuppressLint("MissingPermission")
    private fun connectGattIfNeeded(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        if (activeGatt != null && gattTargetAddress == device.address) return
        try {
            activeGatt?.close()
            activeGatt = null
            gattTargetAddress = device.address
            Log.i(TAG, "Connecting GATT to ${device.name} (${device.address})")
            activeGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt failed", e)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "GATT state change: status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected; discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT disconnected")
                activeGatt?.close()
                activeGatt = null
                gattTargetAddress = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT discovery failed: status=$status")
                return
            }
            Log.i(TAG, "GATT discovered ${gatt.services.size} services")
            for (service in gatt.services) {
                Log.i(TAG, "GATT Service: ${service.uuid}")
                for (ch in service.characteristics) {
                    val props = ch.properties
                    Log.i(TAG, "  GATT Char: ${ch.uuid} props=$props")
                    if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        gatt.readCharacteristic(ch)
                    }
                    if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(ch, true)
                        val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (cccd != null) {
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(cccd)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handleGattData(characteristic)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleGattData(characteristic)
        }
    }

    private fun handleGattData(characteristic: BluetoothGattCharacteristic) {
        val bytes = characteristic.value ?: return
        val hex = bytes.joinToString("") { "%02X".format(it) }
        Log.i(TAG, "GATT read [${characteristic.uuid}] = $hex (${bytes.size} bytes)")

        // Ignore device name characteristic (0x2A00)
        if (characteristic.uuid == UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")) {
            return
        }

        if (characteristic.uuid == UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")) {
            val level = bytes[0].toInt() and 0xFF
            Log.i(TAG, "GATT Battery Level characteristic: $level%")
            if (level in 0..100) {
                onMainBatteryFound(level)
            }
        }
    }

    private fun onMainBatteryFound(level: Int) {
        if (level !in 0..100) return
        val current = latestState
        val caseLevel = current?.caseBattery ?: latestCase?.level ?: caseMemory.last?.level
        val updated = current?.copy(
            leftBattery = level,
            rightBattery = level,
            caseBattery = caseLevel
        ) ?: BudoraState(
            model = BudoraModel.GENERIC_BLUETOOTH,
            leftBattery = level,
            rightBattery = level,
            caseBattery = caseLevel,
            isLeftCharging = false,
            isRightCharging = false,
            isCaseCharging = false,
            isLidOpen = true,
            lidOpenCounter = 0,
            rawLeftInEar = false,
            rawRightInEar = false,
        )
        latestState = updated
        val lastCase = latestCase ?: caseMemory.last
        BudoraRepository.onState(updated, lastCase)
        updateOngoing(updated, lastCase)
    }

    private fun onCaseBatteryFound(level: Int) {
        if (level !in 1..100) return
        val reading = caseMemory.update(level, false)
        latestCase = reading
        val baseState = latestState ?: BudoraState(
            model = BudoraModel.GENERIC_BLUETOOTH,
            leftBattery = 100,
            rightBattery = 100,
            caseBattery = level,
            isLeftCharging = false,
            isRightCharging = false,
            isCaseCharging = false,
            isLidOpen = true,
            lidOpenCounter = 0,
            rawLeftInEar = false,
            rawRightInEar = false,
        )
        val s = baseState.copy(caseBattery = level)
        latestState = s
        BudoraRepository.onState(s, reading)
        updateOngoing(s, reading)
    }

    private fun readDeviceBattery(device: BluetoothDevice): BudoraState? {
        val level = getBatteryLevel(device)
        val mainBattery = level.takeIf { it in 0..100 } ?: return null

        val liveCase = latestState?.caseBattery
        val n = (try { device.alias ?: device.name } catch (e: SecurityException) { null }).orEmpty().lowercase()
        val isSingle = (latestState?.model == BudoraModel.AIRPODS_MAX) ||
            latestState?.isSingleDevice == true ||
            (latestState == null && (n.contains("neckband") || n.contains("rockerz") || n.contains("wireless") || n.contains("headphone") || n.contains("headset") || n.contains("wh-") || n.contains("ch-") || n.contains("wi-") || (!n.contains("buds") && !n.contains("airpod"))))

        val isLeftChg = latestState?.isLeftCharging ?: false
        val isRightChg = latestState?.isRightCharging ?: false

        val leftInCase = !isSingle && (
            (isLeftChg && !isRightChg) ||
            (latestState?.leftBattery == null && latestState?.rightBattery != null)
        )
        val rightInCase = !isSingle && (
            (isRightChg && !isLeftChg) ||
            (latestState?.rightBattery == null && latestState?.leftBattery != null)
        )

        return BudoraState(
            model = latestState?.model ?: BudoraModel.GENERIC_BLUETOOTH,
            leftBattery = if (isSingle) mainBattery else (latestState?.leftBattery ?: mainBattery),
            rightBattery = if (isSingle) null else (latestState?.rightBattery ?: mainBattery),
            caseBattery = if (isSingle) null else liveCase,
            isLeftCharging = isLeftChg,
            isRightCharging = isRightChg,
            isCaseCharging = if (isSingle) false else (latestState?.isCaseCharging ?: false),
            isLidOpen = if (isSingle) false else (latestState?.isLidOpen ?: false),
            lidOpenCounter = latestState?.lidOpenCounter ?: 0,
            rawLeftInEar = latestState?.rawLeftInEar ?: false,
            rawRightInEar = latestState?.rawRightInEar ?: false,
            isSingleDevice = isSingle,
            isLeftInCase = leftInCase,
            isRightInCase = rightInCase,
        )
    }

    private fun getBatteryLevel(device: BluetoothDevice): Int = try {
        val method = device.javaClass.getMethod("getBatteryLevel")
        (method.invoke(device) as? Int) ?: -1
    } catch (e: Exception) {
        -1
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == "android.bluetooth.device.action.METADATA_CHANGED") {
                val key = intent.getIntExtra("android.bluetooth.device.extra.KEY", -1)
                val value = intent.getByteArrayExtra("android.bluetooth.device.extra.VALUE")
                val valueStr = value?.let { String(it, Charsets.UTF_8) }
                    ?: intent.getStringExtra("android.bluetooth.device.extra.VALUE")
                    ?: intent.getIntExtra("android.bluetooth.device.extra.VALUE", -1).takeIf { it > 0 }?.toString()
                Log.i(TAG, "ACTION_METADATA_CHANGED: key=$key, value=$valueStr")
                val intVal = valueStr?.toIntOrNull()
                if (key == 12 && intVal != null && intVal > 0) {
                    onCaseBatteryFound(intVal)
                }
                pollConnectedDeviceBattery()
            } else if (action == "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") {
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                Log.i(TAG, "ACTION_BATTERY_LEVEL_CHANGED: level=$level")
                if (level in 0..100) {
                    onMainBatteryFound(level)
                }
                pollConnectedDeviceBattery()
            } else if (action == BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT) {
                val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                Log.i(TAG, "VENDOR_SPECIFIC_HEADSET_EVENT: cmd=$cmd")
                pollConnectedDeviceBattery()
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        activeGatt?.close()
        activeGatt = null
        gattTargetAddress = null
        batteryJob?.cancel()
        unregisterUnlockReceiver()
        popupHost.destroy()
        scanJob?.cancel()
        scope.cancel()
        alertedThresholdLeft = null
        alertedThresholdRight = null
        alertedThresholdCase = null
        tracker.reset()
        BudoraRepository.onDisconnected()
        com.budora.widget.BudoraWidgetProvider.updateAllWidgets(this)
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun showPopupNow(state: BudoraState, lastCase: LastCaseReading?) {
        val settings = SettingsStore.settings.value
        popupHost.show(state, lastCase, settings)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BudoraService"
        var instance: BudoraService? = null
            private set

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, BudoraService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, BudoraService::class.java))
        }
    }
}
