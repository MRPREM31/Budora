package com.budora.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps the BLE scanner and emits decoded AirPods state.
 *
 * Two details here are load-bearing and easy to get wrong:
 *
 * 1. Results are decoded from [ScanResult.getScanRecord]'s *raw bytes*, not
 *    from getManufacturerSpecificData(). See [ProximityPayload.parseRawScanRecord].
 *
 * 2. The hardware filter matches Apple's company ID and the 0x07 type byte
 *    only -- never the length byte, which varies across firmware. A filter is
 *    mandatory rather than an optimisation: since Android 8.1 the platform
 *    stops unfiltered scans outright when the screen is off.
 */
class BudoraScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
            ?.adapter

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Emit decoded state for every matching advertisement.
     *
     * Cold flow: scanning starts on collection and stops on cancellation, so
     * the radio is never left running past the service that owns it.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun advertisements(
        scanMode: Int = ScanSettings.SCAN_MODE_BALANCED,
        targetAddress: String? = null,
    ): Flow<Advertisement> =
        callbackFlow {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                Log.w(TAG, "No BLE scanner; Bluetooth is off or unavailable")
                close()
                return@callbackFlow
            }

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val fastPairData = result.scanRecord?.getServiceData(FastPairPayload.FAST_PAIR_SERVICE_UUID)
                    if (fastPairData != null) {
                        Log.i(TAG, "FastPair frame from ${result.device.address}: ${fastPairData.joinToString("") { "%02X".format(it) }}")
                        val state = FastPairPayload.parse(fastPairData)
                        if (state != null) {
                            Log.i(TAG, "FastPair parsed state: L=${state.leftBattery} R=${state.rightBattery} Case=${state.caseBattery}")
                            trySend(Advertisement(result.device.address, result.rssi, state))
                            return
                        }
                    }

                    val raw = result.scanRecord?.bytes ?: return
                    val state = ProximityPayload.parseRawScanRecord(raw) ?: return
                    trySend(Advertisement(result.device.address, result.rssi, state))
                }

                // The primary delivery path (see BATCH_REPORT_DELAY_MS).
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE scan failed: $errorCode")
                    close(ScanFailedException(errorCode))
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(scanMode)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(BATCH_REPORT_DELAY_MS)
                .build()

            val filterList = mutableListOf(
                appleProximityFilter(),
                ScanFilter.Builder().setServiceUuid(FastPairPayload.FAST_PAIR_SERVICE_UUID).build(),
                ScanFilter.Builder().setServiceData(FastPairPayload.FAST_PAIR_SERVICE_UUID, null).build(),
            )
            if (targetAddress != null && BluetoothAdapter.checkBluetoothAddress(targetAddress)) {
                filterList.add(ScanFilter.Builder().setDeviceAddress(targetAddress).build())
            }

            try {
                scanner.startScan(filterList, settings, callback)
            } catch (e: SecurityException) {
                Log.e(TAG, "startScan denied: BLUETOOTH_SCAN not held", e)
                close(e)
                return@callbackFlow
            }
            Log.i(TAG, "Scan started, mode=$scanMode, filters=${filterList.size}")

            awaitClose {
                runCatching { scanner.stopScan(callback) }
                    .onFailure { Log.w(TAG, "stopScan failed", it) }
                Log.i(TAG, "Scan stopped")
            }
        }

    /**
     * Match Apple manufacturer data whose first byte is 0x07.
     *
     * The mask is one byte long, covering the type byte alone: the length byte
     * at index 1 differs between firmware generations and matching it would
     * silently drop valid frames.
     *
     * The mask VALUE must be 0xFF. A mask is bitwise -- 0x01 compares only the
     * lowest bit, which let every odd Apple type (0x01, 0x05, 0x0F, 0x13 ...)
     * through the hardware filter and relied on the parser to reject them.
     */
    private fun appleProximityFilter(): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(
            ProximityPayload.APPLE_COMPANY_ID,
            byteArrayOf(ProximityPayload.TYPE_PROXIMITY_PAIRING),
            byteArrayOf(0xFF.toByte()),
        )
        .build()

    data class Advertisement(
        val address: String,
        val rssi: Int,
        val state: BudoraState,
    )

    class ScanFailedException(val errorCode: Int) :
        RuntimeException("BLE scan failed with code $errorCode")

    private companion object {
        const val TAG = "BudoraScanner"

        /**
         * Batch window. The platform rounds anything shorter up to 5 s, so ask
         * for 5 s explicitly rather than pretend a smaller number means more.
         * One frame per window is plenty for a battery readout, and the
         * controller does the buffering, so the AP sleeps between windows.
         */
        const val BATCH_REPORT_DELAY_MS = 1_000L

        /**
         * Bring-up diagnostics: log the raw hex of every advertisement that
         * passes the hardware filter.
         *
         * This used to also drop the ScanFilter entirely, which is never safe
         * from a service: since Android 8.1 the platform suppresses UNFILTERED
         * scans while the screen is off. The filter is now applied
         * unconditionally, so enabling this only adds logging. For a truly
         * unfiltered look at the air, use RawScanProbeTest instead.
         */
        const val DEBUG_RAW = false
    }
}
