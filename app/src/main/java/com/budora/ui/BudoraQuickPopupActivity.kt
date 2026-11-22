package com.budora.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.budora.ui.overlay.BudoraPopupManager
import com.budora.widget.BudoraWidgetProvider

class BudoraQuickPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BudoraPopupManager.showPopup(this)
        BudoraWidgetProvider.updateAllWidgets(this)
        finish()
    }
}

