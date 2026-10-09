package com.ynks.burndown.widget

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ynks.burndown.MainActivity
import com.ynks.burndown.R
import com.ynks.burndown.chart.SprintChartRenderer
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Trend
import kotlin.math.max
import kotlin.math.sqrt

private val Outer = 14.dp
private const val TALL_FROM_DP = 150f

@Composable
fun SprintWidgetContent(
    type: ChartType,
    data: WidgetData,
    showNumber: Boolean = true,
    schemes: Pair<ColorScheme, ColorScheme>? = null,
) {
    val context = LocalContext.current
    val theme = remember(type) { WidgetTheme.of(context, type, schemes) }
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(theme.container)
            .cornerRadius(28.dp)
            .padding(Outer)
            .clickable(openApp),
    ) {
        when (data) {
            WidgetData.NotConfigured -> Message(theme, "Set up Burndown", "Tap to choose a GitHub project")
            is WidgetData.Message -> Message(theme, data.title, data.detail)
            is WidgetData.Ready -> ReadyContent(type, theme, data, showNumber)
        }
    }
}

@Composable
private fun Message(theme: WidgetTheme, title: String, detail: String) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(color = theme.onContainer, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        Text(detail, style = TextStyle(color = theme.onContainer, fontSize = 13.sp), maxLines = 3)
    }
}

@Composable
private fun ReadyContent(type: ChartType, theme: WidgetTheme, data: WidgetData.Ready, showNumber: Boolean) {
    val size = LocalSize.current
    val innerW = size.width.value - 2 * Outer.value
    val innerH = size.height.value - 2 * Outer.value

    if (!showNumber) {
        Chart(type, theme, data, DpSize(innerW.dp, innerH.dp), GlanceModifier.fillMaxSize())
        return
    }
    val big = size.height.value >= TALL_FROM_DP
    val textW = dataColumnWidthDp(data, big)
    val gap = 12f
    val chartW = innerW - textW - gap
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.width(textW.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            DataColumn(theme, data, big)
        }
        if (chartW >= 80f) {
            Spacer(GlanceModifier.width(gap.dp))
            Chart(type, theme, data, DpSize(chartW.dp, innerH.dp), GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

private fun unitOf(data: WidgetData.Ready) = if (data.metric == Metric.POINTS) "pts" else "issues"

private fun numberFontSize(number: String, big: Boolean): Int {
    val base = if (big) 44 else 32
    return when {
        number.length <= 2 -> base
        number.length == 3 -> base - 8
        else -> base - 14
    }
}

private class DataLines(
    val number: String,
    val left: String,
    val total: String,
    val pace: String,
    val trend: Trend?,
    val trendText: String?,
)

private fun dataLines(data: WidgetData.Ready): DataLines {
    val unit = unitOf(data)
    val stats = data.series.stats
    val started = data.series.last != null
    val pace = when {
        !started -> "starts soon"
        stats.requiredPerDay == null -> "sprint ended"
        else -> "needs ${SprintChartRenderer.formatValue(stats.requiredPerDay)}/day"
    }
    return DataLines(
        number = if (started) SprintChartRenderer.formatValue(stats.remaining) else "-",
        left = "$unit left",
        total = if (data.staleReason != null) "offline" else "${SprintChartRenderer.formatValue(stats.total)} $unit total",
        pace = pace,
        trend = stats.trend.takeIf { started },
        trendText = stats.trendDelta.takeIf { started && stats.trend != null }?.let {
            if (stats.trend == Trend.FLAT) "0/day" else "${SprintChartRenderer.formatSigned(it)}/day"
        },
    )
}

// rough guess since remoteviews can not measure text
private fun dataColumnWidthDp(data: WidgetData.Ready, big: Boolean): Float {
    val lines = dataLines(data)
    val numberW = lines.number.length * 0.6f * numberFontSize(lines.number, big)
    val textW = maxOf(
        listOf(lines.left, lines.total, lines.pace).maxOf { it.length } * 6.9f,
        (lines.trendText?.length ?: 0) * 6.9f + 14f,
    )
    return (max(numberW, textW) + 6f).coerceIn(80f, 150f)
}

@Composable
private fun DataColumn(theme: WidgetTheme, data: WidgetData.Ready, big: Boolean) {
    val lines = dataLines(data)
    val centered = TextStyle(color = theme.onContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            lines.number,
            style = TextStyle(
                color = theme.onContainer,
                fontSize = numberFontSize(lines.number, big).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = 1,
        )
        Text(lines.left, style = centered.copy(fontSize = 13.sp), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            lines.total,
            style = if (data.staleReason != null) centered.copy(color = theme.error) else centered,
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = 1,
        )
        Text(lines.pace, style = centered, modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
        lines.trendText?.let { text ->
            val icon = when (lines.trend) {
                Trend.UP -> R.drawable.ic_trend_up
                Trend.DOWN -> R.drawable.ic_trend_down
                else -> null
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        provider = ImageProvider(icon),
                        contentDescription = if (lines.trend == Trend.UP) "pace needed is rising" else "pace needed is falling",
                        colorFilter = ColorFilter.tint(theme.onContainer),
                        modifier = GlanceModifier.size(10.dp),
                    )
                    Spacer(GlanceModifier.width(3.dp))
                }
                Text(text, style = centered.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Chart(type: ChartType, theme: WidgetTheme, data: WidgetData.Ready, size: DpSize, modifier: GlanceModifier) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bitmap = remember(data, size, type, theme) { renderChart(density, size, data, type, theme) }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = if (type == ChartType.BURNDOWN) "Burndown chart" else "Burnup chart",
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

private fun renderChart(density: Float, size: DpSize, data: WidgetData.Ready, type: ChartType, theme: WidgetTheme): Bitmap {
    // stay under the remoteviews bitmap limit
    val maxPixels = 900_000f
    var w = size.width.value * density
    var h = max(size.height.value, 40f) * density
    var scale = density
    if (w * h > maxPixels) {
        val f = sqrt(maxPixels / (w * h))
        w *= f; h *= f; scale *= f
    }
    return SprintChartRenderer.render(data.series, type, theme.chart, w.toInt(), h.toInt(), scale, drawPlate = false, simple = true)
}
