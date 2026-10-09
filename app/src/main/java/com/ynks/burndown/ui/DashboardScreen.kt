package com.ynks.burndown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ynks.burndown.chart.SprintChartRenderer
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.domain.Trend
import com.ynks.burndown.domain.dayNumber
import com.ynks.burndown.domain.totalDays
import com.ynks.burndown.widget.WidgetData
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    data: WidgetData?,
    resolution: Resolution,
    onResolution: (Resolution) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    when (data) {
        null -> Column(Modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(Modifier.size(56.dp))
        }
        WidgetData.NotConfigured -> EmptyState("Not set up yet", "Connect a GitHub project to see your sprint.", contentPadding, onOpenSettings)
        is WidgetData.Message -> EmptyState(data.title, data.detail, contentPadding, onOpenSettings)
        is WidgetData.Ready -> Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Header(data)
            ChoiceGroup(
                options = listOf(Resolution.DAILY to "Per day", Resolution.HOURLY to "Per hour"),
                selected = resolution,
                onSelect = onResolution,
            )
            ChartSection("Burndown", data, ChartType.BURNDOWN)
            ChartSection("Burnup", data, ChartType.BURNUP)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) { Text("Open settings") }
    }
}

@Composable
private fun Header(data: WidgetData.Ready) {
    val series = data.series
    val last = series.last
    val unit = if (data.metric == Metric.POINTS) "pts" else "issues"
    val day = series.dayNumber()
    val progress = when {
        day < 1 -> "starts soon"
        day > series.totalDays -> "ended"
        else -> "day $day of ${series.totalDays}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${data.sprintTitle} · $progress", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                last?.let { SprintChartRenderer.formatValue(it.remaining) } ?: "-",
                style = MaterialTheme.typography.displayLargeEmphasized,
            )
            Text(
                if (last == null) "not started" else "$unit left",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        if (last != null) {
            val ahead = series.aheadBy
            val status = when {
                abs(ahead) < 0.5 -> "on track"
                ahead > 0 -> "${SprintChartRenderer.formatValue(ahead)} ahead"
                else -> "${SprintChartRenderer.formatValue(-ahead)} behind"
            }
            Text(
                "${SprintChartRenderer.formatValue(last.completed)} of ${SprintChartRenderer.formatValue(last.total)} done · $status",
                style = MaterialTheme.typography.bodyLarge,
                color = if (ahead <= -0.5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        series.stats.requiredPerDay?.let { perDay ->
            Text(
                "needs ${SprintChartRenderer.formatValue(perDay)} $unit/day to finish",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val delta = series.stats.trendDelta
            val trend = series.stats.trend
            if (delta != null && trend != null) {
                val arrow = when (trend) {
                    Trend.UP -> "▲ "
                    Trend.DOWN -> "▼ "
                    else -> ""
                }
                val change = if (trend == Trend.FLAT) "0/day" else "${SprintChartRenderer.formatSigned(delta)}/day"
                Text(
                    "$arrow$change vs yesterday",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        data.staleReason?.let {
            Text("Offline: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ChartSection(title: String, data: WidgetData.Ready, type: ChartType) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
        ChartImage(data.series, type, Modifier.fillMaxWidth())
    }
}
