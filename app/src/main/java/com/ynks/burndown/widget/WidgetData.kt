package com.ynks.burndown.widget

import android.content.Context
import com.ynks.burndown.data.cache.SprintStore
import com.ynks.burndown.data.cache.StatusKind
import com.ynks.burndown.data.settings.SettingsRepository
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.SprintSeries
import com.ynks.burndown.domain.SprintSeriesBuilder
import java.time.Instant
import java.time.ZoneId

sealed interface WidgetData {
    data object NotConfigured : WidgetData
    data class Message(val title: String, val detail: String) : WidgetData
    data class Ready(
        val sprintTitle: String,
        val series: SprintSeries,
        val metric: Metric,
        val updatedAtMillis: Long,
        val staleReason: String?,
    ) : WidgetData
}

object WidgetDataLoader {
    suspend fun load(context: Context, now: Instant = Instant.now()): WidgetData {
        val settings = SettingsRepository(context).current()
        if (!settings.isConfigured) return WidgetData.NotConfigured

        val state = SprintStore(context).current()
        val status = state.status
        if (status.kind == StatusKind.AUTH) {
            return WidgetData.Message("Check GitHub token", status.message ?: "Token rejected")
        }
        if (status.kind == StatusKind.NO_SPRINT) {
            return WidgetData.Message("No active sprint", status.message.orEmpty())
        }
        val snapshot = state.snapshot
        if (snapshot == null || snapshot.metricKey != settings.metricKey) {
            val detail = if (status.kind == StatusKind.NONE || status.kind == StatusKind.OK) "Waiting for the first sync"
            else status.message ?: "Sync failed"
            return WidgetData.Message("Syncing", detail)
        }
        val series = SprintSeriesBuilder.build(
            snapshot = snapshot,
            history = state.historyFor(snapshot),
            now = now,
            zone = ZoneId.systemDefault(),
            resolution = settings.resolution,
        )
        val stale = when (status.kind) {
            StatusKind.NETWORK, StatusKind.OTHER -> status.message ?: "Last sync failed"
            else -> null
        }
        return WidgetData.Ready(snapshot.iteration.title, series, settings.metric, snapshot.fetchedAt, stale)
    }
}
