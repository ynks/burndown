package com.ynks.burndown.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.runComposition
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.Iteration
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.domain.SprintItem
import com.ynks.burndown.domain.SprintSeriesBuilder
import com.ynks.burndown.domain.SprintSnapshot
import com.ynks.burndown.ui.BaselineDark
import com.ynks.burndown.ui.BaselineLight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class, androidx.glance.ExperimentalGlanceApi::class)
object WidgetTestSupport {
    private fun ms(s: String) = Instant.parse(s).toEpochMilli()
    val now: Instant = Instant.parse("2026-10-09T15:00:00Z")

    val snapshot = SprintSnapshot(
        Iteration("it", "Sprint 12", "2026-10-05", 10),
        listOf(
            SprintItem(3.0, ms("2026-10-05T16:10:00Z")), SprintItem(2.0, ms("2026-10-06T09:45:00Z")),
            SprintItem(5.0, ms("2026-10-06T14:20:00Z")), SprintItem(1.0, ms("2026-10-07T11:05:00Z")),
            SprintItem(3.0, ms("2026-10-08T10:00:00Z")), SprintItem(2.0, ms("2026-10-09T08:30:00Z")),
            SprintItem(5.0), SprintItem(3.0), SprintItem(2.0), SprintItem(1.0),
        ),
        ms("2026-10-09T15:00:00Z"), "points",
    )

    fun ready(resolution: Resolution = Resolution.DAILY) = WidgetData.Ready(
        sprintTitle = "Sprint 12",
        series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, ZoneId.of("UTC"), resolution),
        metric = Metric.POINTS,
        updatedAtMillis = now.toEpochMilli(),
        staleReason = null,
    )

    private class FixedWidget(private val type: ChartType, private val data: WidgetData, private val showNumber: Boolean) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { GlanceTheme { SprintWidgetContent(type, data, showNumber, BaselineLight to BaselineDark) } }
        }
    }

    fun renderWidget(type: ChartType, data: WidgetData, wDp: Int, hDp: Int, night: Boolean, showNumber: Boolean = true): Bitmap {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = base.createConfigurationContext(config)
        val density = context.resources.displayMetrics.density
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
        }
        val views = runBlocking { FixedWidget(type, data, showNumber).runComposition(context, options = options).first() }
        val wPx = (wDp * density).toInt()
        val hPx = (hDp * density).toInt()
        val view = views.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, wPx, hPx)
        return Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/chart-samples").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
