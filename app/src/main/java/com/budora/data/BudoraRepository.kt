package com.budora.data

import com.budora.ble.BudoraState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for what the UI and the notification both show.
 *
 * A process-wide singleton rather than an injected dependency: the foreground
 * service writes, the Activity reads, and they have separate lifecycles, so the
 * state has to outlive both. Small enough that a DI framework would be
 * ceremony; if this grows, that is the point to revisit.
 */
object BudoraRepository {

    private val _state = MutableStateFlow<BudoraConnection>(BudoraConnection.Disconnected)
    val state: StateFlow<BudoraConnection> = _state.asStateFlow()

    /**
     * The user's own name for the pods, e.g. "UV-AirPods".
     *
     * Kept separate from [state] because it arrives from a different source
     * at a different time: the classic Bluetooth link knows the name at
     * connect, while the battery levels come later from the BLE beacon.
     */
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    fun onDeviceName(name: String?) {
        _deviceName.value = name
    }

    fun onDisconnected() {
        _state.value = BudoraConnection.Disconnected
    }

    /** Connected over classic Bluetooth, but no beacon decoded yet. */
    fun onSearching() {
        _state.value = BudoraConnection.Searching
    }

    fun onState(state: BudoraState, lastCase: LastCaseReading?) {
        _state.value = BudoraConnection.Live(state, lastCase)
    }
}

/**
 * Connection is a state machine, not a nullable [BudoraState]. "Not connected"
 * and "connected but no reading yet" produce very different UI, and collapsing
 * them into null loses that.
 */
sealed interface BudoraConnection {
    data object Disconnected : BudoraConnection
    data object Searching : BudoraConnection
    /**
     * @param lastCase the most recent case reading, live or remembered. Null
     *   only when the case has never been seen. Compare [BudoraState.caseBattery]
     *   to tell live from remembered: live is non-null.
     */
    data class Live(val state: BudoraState, val lastCase: LastCaseReading? = null) : BudoraConnection
}
