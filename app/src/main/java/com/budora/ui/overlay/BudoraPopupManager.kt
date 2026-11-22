package com.budora.ui.overlay

import android.content.Context
import android.content.Intent
import android.util.Log
import com.budora.data.BudoraConnection
import com.budora.data.BudoraRepository
import com.budora.data.SettingsStore
import com.budora.service.BudoraService
import com.budora.ui.MainActivity

object BudoraPopupManager {
    private const val TAG = "BudoraPopupManager"
    private var globalHost: ConnectPopupHost? = null

    fun showPopup(context: Context) {
        val appContext = context.applicationContext
        val conn = BudoraRepository.state.value

        // When disconnected, NEVER show fake battery numbers. Open MainActivity instead.
        if (conn !is BudoraConnection.Live) {
            Log.d(TAG, "Not connected (state is $conn); opening MainActivity")
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appContext.startActivity(intent)
            return
        }

        val settings = SettingsStore.settings.value
        val state = conn.state
        val lastCase = conn.lastCase

        val service = BudoraService.instance
        if (service != null) {
            Log.d(TAG, "Showing popup via active BudoraService")
            service.showPopupNow(state, lastCase)
        } else {
            Log.d(TAG, "Showing popup via globalHost")
            if (globalHost == null) {
                globalHost = ConnectPopupHost(appContext)
            }
            globalHost?.show(state, lastCase, settings)
        }
    }
}
