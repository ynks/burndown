package com.ynks.burndown.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ynks.burndown.data.cache.StatusKind
import com.ynks.burndown.data.cache.SprintStore
import com.ynks.burndown.data.cache.SyncStatus
import com.ynks.burndown.data.github.GitHubApi
import com.ynks.burndown.data.github.GitHubAuthException
import com.ynks.burndown.data.settings.SettingsRepository
import com.ynks.burndown.domain.CurrentIteration
import com.ynks.burndown.domain.SprintSnapshot
import com.ynks.burndown.widget.WidgetUpdater
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        val store = SprintStore(applicationContext)
        val config = settings.current()
        val token = settings.token()
        if (!config.isConfigured || token == null) {
            WidgetUpdater.updateAll(applicationContext)
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val result = try {
            val api = GitHubApi(token)
            val iterations = api.loadIterations(config.projectId, config.iterationFieldName)
            val today = LocalDate.now(ZoneId.systemDefault())
            val current = CurrentIteration.pick(iterations, today)
            if (current == null) {
                val next = CurrentIteration.nextUpcoming(iterations, today)
                val message = next?.let { "No active sprint. \"${it.title}\" starts ${it.startDate}." }
                    ?: "No active sprint in \"${config.iterationFieldName}\"."
                store.update { it.copy(snapshot = null, status = SyncStatus(StatusKind.NO_SPRINT, message, now)) }
            } else {
                val items = api.loadSprintItems(
                    projectId = config.projectId,
                    iterationFieldName = config.iterationFieldName,
                    iterationId = current.id,
                    metric = config.metric,
                    pointsFieldName = config.pointsFieldName.ifBlank { null },
                )
                store.recordSnapshot(SprintSnapshot(current, items, now, config.metricKey), now)
            }
            Result.success()
        } catch (e: GitHubAuthException) {
            store.update { it.copy(status = SyncStatus(StatusKind.AUTH, e.message, now)) }
            Result.success()
        } catch (e: IOException) {
            store.update { it.copy(status = SyncStatus(StatusKind.NETWORK, e.message, now)) }
            Result.retry()
        } catch (e: Exception) {
            store.update { it.copy(status = SyncStatus(StatusKind.OTHER, e.message, now)) }
            Result.success()
        }
        WidgetUpdater.updateAll(applicationContext)
        return result
    }
}
