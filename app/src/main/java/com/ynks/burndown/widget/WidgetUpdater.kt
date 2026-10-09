package com.ynks.burndown.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        BurndownWidget().updateAll(context)
        BurnupWidget().updateAll(context)
        BurndownChartWidget().updateAll(context)
        BurnupChartWidget().updateAll(context)
    }
}
