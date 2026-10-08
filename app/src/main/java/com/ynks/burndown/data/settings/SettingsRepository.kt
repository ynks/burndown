package com.ynks.burndown.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val owner: String = "",
    val projectNumber: Int = 0,
    val projectId: String = "",
    val projectTitle: String = "",
    val iterationFieldName: String = "",
    val metric: Metric = Metric.COUNT,
    val pointsFieldName: String = "",
    val resolution: Resolution = Resolution.DAILY,
    val hasToken: Boolean = false,
) {
    val isConfigured: Boolean
        get() = hasToken && projectId.isNotBlank() && iterationFieldName.isNotBlank() &&
            (metric == Metric.COUNT || pointsFieldName.isNotBlank())

    val metricKey: String
        get() = "$projectId|$iterationFieldName|" + if (metric == Metric.POINTS) "points:$pointsFieldName" else "count"
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun token(): String? =
        store.data.first()[TOKEN]?.let(TokenCipher::decrypt)

    suspend fun save(token: String?, settings: AppSettings) {
        store.edit { prefs ->
            if (!token.isNullOrBlank()) prefs[TOKEN] = TokenCipher.encrypt(token.trim())
            prefs[OWNER] = settings.owner
            prefs[PROJECT_NUMBER] = settings.projectNumber
            prefs[PROJECT_ID] = settings.projectId
            prefs[PROJECT_TITLE] = settings.projectTitle
            prefs[ITERATION_FIELD] = settings.iterationFieldName
            prefs[METRIC] = settings.metric.name
            prefs[POINTS_FIELD] = settings.pointsFieldName
            prefs[RESOLUTION] = settings.resolution.name
        }
    }

    suspend fun setResolution(resolution: Resolution) {
        store.edit { it[RESOLUTION] = resolution.name }
    }

    private fun Preferences.toSettings() = AppSettings(
        owner = this[OWNER].orEmpty(),
        projectNumber = this[PROJECT_NUMBER] ?: 0,
        projectId = this[PROJECT_ID].orEmpty(),
        projectTitle = this[PROJECT_TITLE].orEmpty(),
        iterationFieldName = this[ITERATION_FIELD].orEmpty(),
        metric = this[METRIC]?.let { runCatching { Metric.valueOf(it) }.getOrNull() } ?: Metric.COUNT,
        pointsFieldName = this[POINTS_FIELD].orEmpty(),
        resolution = this[RESOLUTION]?.let { runCatching { Resolution.valueOf(it) }.getOrNull() } ?: Resolution.DAILY,
        hasToken = !this[TOKEN].isNullOrBlank(),
    )

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val OWNER = stringPreferencesKey("owner")
        val PROJECT_NUMBER = intPreferencesKey("project_number")
        val PROJECT_ID = stringPreferencesKey("project_id")
        val PROJECT_TITLE = stringPreferencesKey("project_title")
        val ITERATION_FIELD = stringPreferencesKey("iteration_field")
        val METRIC = stringPreferencesKey("metric")
        val POINTS_FIELD = stringPreferencesKey("points_field")
        val RESOLUTION = stringPreferencesKey("resolution")
    }
}
