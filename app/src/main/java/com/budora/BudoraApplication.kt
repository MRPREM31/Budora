package com.budora

import android.app.Application
import com.budora.data.SettingsStore
import com.budora.service.BondReceiver

class BudoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Process-lifetime registration, alongside the manifest one that wakes
        // a killed process; BondReceiver de-duplicates the two. Only the
        // receiver registers here. Starting the service from
        // Application.onCreate() is not permitted -- the process is still in
        // the background at that point -- so the already-connected check runs
        // from MainActivity instead.
        SettingsStore.init(this)
        BondReceiver.register(this)
    }
}
