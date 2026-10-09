package com.ynks.burndown.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ynks.burndown.data.cache.SprintStore
import com.ynks.burndown.data.github.GitHubApi
import com.ynks.burndown.data.github.ProjectInfo
import com.ynks.burndown.data.settings.AppSettings
import com.ynks.burndown.data.settings.SettingsRepository
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.sync.SyncScheduler
import com.ynks.burndown.widget.WidgetData
import com.ynks.burndown.widget.WidgetDataLoader
import com.ynks.burndown.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FormState(
    val token: String = "",
    val owner: String = "",
    val projectNumber: String = "",
    val project: ProjectInfo? = null,
    val iterationField: String = "",
    val metric: Metric = Metric.COUNT,
    val pointsField: String = "",
    val resolution: Resolution = Resolution.DAILY,
    val hasStoredToken: Boolean = false,
    val loadingProject: Boolean = false,
    val error: String? = null,
) {
    val canLoadProject: Boolean
        get() = (token.isNotBlank() || hasStoredToken) && owner.isNotBlank() && projectNumber.toIntOrNull() != null
    val canSave: Boolean
        get() = project != null && iterationField.isNotBlank() && (metric == Metric.COUNT || pointsField.isNotBlank())
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val store = SprintStore(app)

    val form = MutableStateFlow(FormState())

    val dashboard: StateFlow<WidgetData?> = combine(settingsRepo.settings, store.state) { _, _ ->
        WidgetDataLoader.load(app)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(SyncScheduler.ONCE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            form.update {
                it.copy(
                    owner = s.owner,
                    projectNumber = if (s.projectNumber > 0) s.projectNumber.toString() else "",
                    iterationField = s.iterationFieldName,
                    metric = s.metric,
                    pointsField = s.pointsFieldName,
                    resolution = s.resolution,
                    hasStoredToken = s.hasToken,
                )
            }
            if (s.isConfigured) loadProject(silent = true)
        }
    }

    fun edit(transform: (FormState) -> FormState) = form.update(transform)

    fun loadProject(silent: Boolean = false) {
        val f = form.value
        val number = f.projectNumber.toIntOrNull() ?: return
        viewModelScope.launch {
            form.update { it.copy(loadingProject = true, error = null) }
            try {
                val token = f.token.trim().ifBlank { settingsRepo.token() }
                    ?: throw IllegalStateException("Enter a GitHub token")
                val project = GitHubApi(token).loadProject(f.owner.trim(), number)
                form.update { cur ->
                    cur.copy(
                        project = project,
                        iterationField = cur.iterationField.takeIf { it in project.iterationFields }
                            ?: project.iterationFields.firstOrNull().orEmpty(),
                        pointsField = cur.pointsField.takeIf { it in project.numberFields }
                            ?: project.numberFields.firstOrNull { n ->
                                listOf("point", "estimate", "size").any { n.contains(it, ignoreCase = true) }
                            } ?: project.numberFields.firstOrNull().orEmpty(),
                        loadingProject = false,
                    )
                }
            } catch (e: Exception) {
                form.update { it.copy(loadingProject = false, error = if (silent) null else e.message ?: "Couldn't load project") }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val f = form.value
        val project = f.project ?: return
        viewModelScope.launch {
            settingsRepo.save(
                token = f.token.takeIf { it.isNotBlank() },
                settings = AppSettings(
                    owner = f.owner.trim(),
                    projectNumber = f.projectNumber.toInt(),
                    projectId = project.id,
                    projectTitle = project.title,
                    iterationFieldName = f.iterationField,
                    metric = f.metric,
                    pointsFieldName = if (f.metric == Metric.POINTS) f.pointsField else "",
                    resolution = f.resolution,
                    hasToken = true,
                ),
            )
            form.update { it.copy(token = "", hasStoredToken = true) }
            SyncScheduler.schedule(getApplication())
            SyncScheduler.syncNow(getApplication())
            WidgetUpdater.updateAll(getApplication())
            onDone()
        }
    }

    fun setResolution(resolution: Resolution) {
        form.update { it.copy(resolution = resolution) }
        viewModelScope.launch {
            settingsRepo.setResolution(resolution)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun syncNow() = SyncScheduler.syncNow(getApplication())
}
