package com.budora.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budora.ble.BudoraModel
import com.budora.ble.BudoraState
import com.budora.data.ColorTheme
import com.budora.data.DarkMode
import com.budora.data.LastCaseReading
import com.budora.data.PopupPosition
import com.budora.data.SettingsStore
import com.budora.ui.overlay.ConnectPopupHost

/**
 * Debug-only: shows the connect popup on demand, without AirPods.
 *
 * The popup's whole reason to exist is that it appears over other apps when
 * the pods connect, which makes it awkward to iterate on -- every visual
 * tweak otherwise costs a disconnect/reconnect cycle, and checking a design
 * across four palettes in two brightness modes by hand is eight of them.
 * This renders it with fixed battery values so it can be screenshotted
 * unattended.
 *
 * Lives in the debug source set, so it cannot ship: there is no such class in
 * a release build, and the manifest entry that exposes it is debug-only too.
 *
 * ```
 * adb shell am broadcast -a com.budora.SHOW_POPUP \
 *   -n com.budora/.debug.PopupPreviewReceiver \
 *   --es theme ocean --es dark true
 * ```
 */
class PopupPreviewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val theme = ColorTheme.fromKey(intent.getStringExtra(EXTRA_THEME))
        val mode = when (intent.getStringExtra(EXTRA_DARK)) {
            "true" -> DarkMode.DARK
            "false" -> DarkMode.LIGHT
            else -> DarkMode.SYSTEM
        }

        // Apply to the real store as well, so the Activity behind the popup
        // re-themes to match. Screenshotting the two together is the point:
        // the popup has to be judged against the app it appears over, not on
        // its own.
        if (intent.getBooleanExtra(EXTRA_APPLY, false)) {
            SettingsStore.update { it.copy(colorTheme = theme, darkMode = mode) }
        }

        val settings = SettingsStore.settings.value.copy(
            colorTheme = theme,
            darkMode = mode,
            popupPosition = PopupPosition.fromKey(intent.getStringExtra(EXTRA_POSITION)),
            // Never auto-dismiss: the screenshot has to be taken after the
            // broadcast returns, and a timer racing the capture would make the
            // run flaky for no benefit.
            autoDismissPopup = false,
        )

        Log.d(TAG, "preview: theme=${settings.colorTheme} dark=${settings.darkMode}")

        // A fresh host per broadcast, torn down by the DISMISS action below.
        // Static state would leak the window across an app restart.
        host?.destroy()
        host = ConnectPopupHost(context.applicationContext).also {
            it.show(PREVIEW_STATE, PREVIEW_CASE, settings)
        }
    }

    private companion object {
        const val TAG = "PopupPreview"
        const val EXTRA_THEME = "theme"
        const val EXTRA_DARK = "dark"
        const val EXTRA_POSITION = "position"

        /** Also write the theme to the real store, so the app behind re-themes. */
        const val EXTRA_APPLY = "apply"

        var host: ConnectPopupHost? = null

        /** Levels chosen to span all three battery colour bands at once. */
        val PREVIEW_STATE = BudoraState(
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
        val PREVIEW_CASE = LastCaseReading(level = 15, isCharging = false, seenAtMs = 0L)
    }
}
