package com.budora.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.budora.R
import com.budora.data.CaseMemory
import com.budora.data.BudoraConnection
import com.budora.data.BudoraRepository
import com.budora.service.BatteryGlyph
import com.budora.ui.BudoraQuickPopupActivity
import com.budora.ui.MainActivity

class BudoraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, BudoraWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateWidget(context, appWidgetManager, id)
                    }
                }
            } catch (e: Exception) {
                // Ignore widget update errors safely
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_budora)
            val conn = BudoraRepository.state.value
            val isConnected = conn is BudoraConnection.Live

            // Click action:
            // When connected -> open the battery drawer card
            // When disconnected -> open MainActivity directly (no fake popup numbers)
            val clickIntent = if (isConnected) {
                Intent(context, BudoraQuickPopupActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val (left, right, case, isRemembered) = when (conn) {
                is BudoraConnection.Live -> {
                    val name = BudoraRepository.deviceName.value ?: context.getString(R.string.app_name)
                    views.setTextViewText(R.id.widget_title, name)
                    views.setTextViewText(
                        R.id.widget_status,
                        context.getString(R.string.status_connected) + " • " + context.getString(R.string.widget_tap_hint)
                    )
                    val rawCase = conn.state.caseBattery?.takeIf { it > 0 }
                        ?: conn.lastCase?.level?.takeIf { it > 0 }
                        ?: CaseMemory.persistent(context).last?.level?.takeIf { it > 0 }
                    val rem = conn.state.caseBattery == null
                    Tuple4(conn.state.leftBattery, conn.state.rightBattery, rawCase, rem)
                }
                is BudoraConnection.Searching -> {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_status, context.getString(R.string.status_searching))
                    Tuple4(null, null, null, false)
                }
                is BudoraConnection.Disconnected -> {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(
                        R.id.widget_status,
                        context.getString(R.string.status_disconnected)
                    )
                    // When disconnected, show '-' for Left, Right, and Case (no fake/misleading numbers!)
                    Tuple4(null, null, null, false)
                }
            }

            views.setImageViewBitmap(
                R.id.widget_glyph_left,
                BatteryGlyph.render(context, left)
            )
            views.setImageViewBitmap(
                R.id.widget_glyph_right,
                BatteryGlyph.render(context, right)
            )
            views.setImageViewBitmap(
                R.id.widget_glyph_case,
                BatteryGlyph.render(
                    context = context,
                    level = case,
                    isCase = true,
                    isRemembered = isRemembered
                )
            )

            views.setImageViewBitmap(
                R.id.widget_icon,
                com.budora.data.AppLogoManager.getLogoBitmap(context)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
