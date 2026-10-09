package com.ynks.burndown.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.sync.SyncScheduler

abstract class SprintWidget(private val type: ChartType, private val showNumber: Boolean) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataLoader.load(context)
        provideContent {
            GlanceTheme { SprintWidgetContent(type, data, showNumber) }
        }
    }
}

class BurndownWidget : SprintWidget(ChartType.BURNDOWN, showNumber = true)

class BurnupWidget : SprintWidget(ChartType.BURNUP, showNumber = true)

class BurndownChartWidget : SprintWidget(ChartType.BURNDOWN, showNumber = false)

class BurnupChartWidget : SprintWidget(ChartType.BURNUP, showNumber = false)

abstract class SprintWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncScheduler.schedule(context)
        SyncScheduler.syncNow(context)
    }
}

class BurndownWidgetReceiver : SprintWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BurndownWidget()
}

class BurnupWidgetReceiver : SprintWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BurnupWidget()
}

class BurndownChartWidgetReceiver : SprintWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BurndownChartWidget()
}

class BurnupChartWidgetReceiver : SprintWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BurnupChartWidget()
}
