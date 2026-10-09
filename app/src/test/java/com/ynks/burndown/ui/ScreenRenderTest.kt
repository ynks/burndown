package com.ynks.burndown.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ynks.burndown.data.github.ProjectInfo
import com.ynks.burndown.data.settings.AppSettings
import com.ynks.burndown.domain.Iteration
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.domain.SprintItem
import com.ynks.burndown.domain.SprintSeriesBuilder
import com.ynks.burndown.domain.SprintSnapshot
import com.ynks.burndown.widget.WidgetData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun ready(resolution: Resolution): WidgetData.Ready {
        val start = LocalDate.now().minusDays(4)
        fun at(day: Int, hour: Int) = start.plusDays(day.toLong()).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = SprintSnapshot(
            Iteration("it", "Sprint 12", start.toString(), 10),
            listOf(
                SprintItem(3.0, at(0, 16)), SprintItem(2.0, at(1, 9)), SprintItem(5.0, at(1, 14)),
                SprintItem(1.0, at(2, 11)), SprintItem(3.0, at(3, 10)), SprintItem(2.0, at(4, 1)),
                SprintItem(5.0), SprintItem(3.0), SprintItem(2.0), SprintItem(1.0),
            ),
            System.currentTimeMillis(), "points",
        )
        return WidgetData.Ready(
            "Sprint 12",
            SprintSeriesBuilder.build(snapshot, emptyMap(), Instant.now(), ZoneId.systemDefault(), resolution),
            Metric.POINTS, System.currentTimeMillis(), null,
        )
    }

    private val settings = AppSettings(
        owner = "ynks", projectNumber = 3, projectId = "id", projectTitle = "Roadmap",
        iterationFieldName = "Sprint", metric = Metric.POINTS, pointsFieldName = "Story points", hasToken = true,
    )
    private val form = FormState(
        owner = "ynks", projectNumber = "3",
        project = ProjectInfo("id", "Roadmap", listOf("Sprint"), listOf("Story points")),
        iterationField = "Sprint", metric = Metric.POINTS, pointsField = "Story points", hasStoredToken = true,
    )

    private fun show(name: String, dashboard: WidgetData?, settings: AppSettings?, form: FormState, settingsOpen: Boolean?) {
        compose.setContent {
            BurndownTheme {
                BurndownApp(
                    form = form, settings = settings, dashboard = dashboard, syncing = false,
                    onEdit = {}, onLoadProject = {}, onSave = {}, onResolution = {}, onSync = {},
                    initialSettingsOpen = settingsOpen,
                )
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/chart-samples").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun dashboard() = show("app-dashboard", ready(Resolution.DAILY), settings, form, false)

    @Test
    @Config(qualifiers = "w390dp-h1300dp-xxhdpi")
    fun dashboardFull() = show("app-dashboard-full", ready(Resolution.HOURLY), settings.copy(resolution = Resolution.HOURLY), form, false)

    @Test
    fun settings() = show("app-settings", null, settings, form, true)

    @Test
    @Config(qualifiers = "w390dp-h1100dp-xxhdpi")
    fun settingsFull() = show("app-settings-full", null, settings, form, true)

    @Test
    fun firstRun() = show("app-first-run", WidgetData.NotConfigured, AppSettings(), FormState(), null)
}
