package com.ynks.burndown.chart

import android.graphics.Bitmap
import android.graphics.Color
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.Iteration
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.domain.SprintItem
import com.ynks.burndown.domain.SprintSeries
import com.ynks.burndown.domain.SprintSeriesBuilder
import com.ynks.burndown.domain.SprintSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class ChartRenderTest {
    private val utc = ZoneId.of("UTC")
    private val iteration = Iteration("it", "Sprint 12", "2026-10-05", 10)
    private val now = Instant.parse("2026-10-09T15:00:00Z")

    private fun ms(s: String) = Instant.parse(s).toEpochMilli()

    private val snapshot = SprintSnapshot(
        iteration,
        listOf(
            SprintItem(3.0, ms("2026-10-05T16:10:00Z")),
            SprintItem(2.0, ms("2026-10-06T09:45:00Z")),
            SprintItem(5.0, ms("2026-10-06T14:20:00Z")),
            SprintItem(1.0, ms("2026-10-07T11:05:00Z")),
            SprintItem(3.0, ms("2026-10-08T10:00:00Z")),
            SprintItem(2.0, ms("2026-10-09T08:30:00Z")),
            SprintItem(5.0), SprintItem(3.0), SprintItem(2.0), SprintItem(1.0),
        ),
        ms("2026-10-09T15:00:00Z"),
        "points",
    )

    private val light = ChartColors(
        primary = Color.parseColor("#3F5F90"),
        onPrimary = Color.WHITE,
        secondary = Color.parseColor("#8A4A9A"),
        onSecondary = Color.WHITE,
        trend = Color.parseColor("#43474E"),
        grid = Color.parseColor("#96747780"),
        label = Color.parseColor("#43474E"),
        plate = Color.parseColor("#FDFBFF"),
    )
    private val dark = ChartColors(
        primary = Color.parseColor("#A7C8FF"),
        onPrimary = Color.parseColor("#0A305F"),
        secondary = Color.parseColor("#E3B5F0"),
        onSecondary = Color.parseColor("#4A1C59"),
        trend = Color.parseColor("#C3C6CF"),
        grid = Color.parseColor("#968E9099"),
        label = Color.parseColor("#C3C6CF"),
        plate = Color.parseColor("#1A1B20"),
    )

    private fun series(resolution: Resolution): SprintSeries =
        SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, resolution)

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/chart-samples").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rendersAllVariants() {
        for (resolution in Resolution.entries) for (type in ChartType.entries) {
            for ((scheme, colors) in listOf("light" to light, "dark" to dark)) {
                val bitmap = SprintChartRenderer.render(series(resolution), type, colors, 960, 480, 3f)
                assertEquals(960, bitmap.width)
                val opaque = (0 until bitmap.width step 7).sumOf { x ->
                    (0 until bitmap.height step 7).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
                }
                assertTrue("$resolution $type $scheme drew nothing", opaque > 50)
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, 0)))
                save(bitmap, "${type.name.lowercase()}-${resolution.name.lowercase()}-$scheme")
            }
        }
    }

    @Test
    fun linesAreStraightDiagonalsNotHeldSteps() {
        for (resolution in Resolution.entries) {
            val series = series(resolution)
            for (type in ChartType.entries) {
                val lines = if (type == ChartType.BURNDOWN) listOf(SprintChartRenderer.Line.REMAINING)
                else listOf(SprintChartRenderer.Line.TOTAL, SprintChartRenderer.Line.COMPLETED)
                for (line in lines) {
                    val v = SprintChartRenderer.vertices(series, type, line, 960f, 480f, 3f)
                    assertTrue(v.zipWithNext().all { (a, b) -> b.x > a.x })
                    if (resolution == Resolution.DAILY) {
                        assertEquals("$type $line", series.points.size, v.size)
                    } else {
                        assertTrue(v.size < series.points.size || line == SprintChartRenderer.Line.TOTAL)
                        assertTrue("$type $line", v.drop(1).dropLast(1).zipWithNext().all { (a, b) -> a.y != b.y } || v.size <= 2)
                    }
                }
            }
        }
    }

    @Test
    fun futureDaysStayBlank() {
        val bitmap = SprintChartRenderer.render(series(Resolution.DAILY), ChartType.BURNDOWN, light, 960, 480, 3f)
        val strokeColor = light.primary
        val rightMostSeriesPixel = (bitmap.width - 1 downTo 0).first { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == strokeColor }
        }
        assertTrue("series should stop around mid-chart, got x=$rightMostSeriesPixel", rightMostSeriesPixel < bitmap.width * 0.65)
    }

    @Test
    fun niceCeilGivesTidyAxisMaximums() {
        assertEquals(1.0, SprintChartRenderer.niceCeil(0.3), 0.0)
        assertEquals(4.0, SprintChartRenderer.niceCeil(3.4), 0.0)
        assertEquals(30.0, SprintChartRenderer.niceCeil(26.0), 0.0)
        assertEquals(25.0, SprintChartRenderer.niceCeil(23.0), 0.0)
        assertEquals(40.0, SprintChartRenderer.niceCeil(31.0), 0.0)
        assertEquals(120.0, SprintChartRenderer.niceCeil(101.0), 0.0)
        assertEquals(15.0, SprintChartRenderer.niceCeil(14.0), 0.0)
        assertEquals("12.5", SprintChartRenderer.formatValue(12.5))
        assertEquals("14", SprintChartRenderer.formatValue(14.0))
    }

    @Test
    fun rendersTheScopeGrowthCase() {
        // scope grew a lot after the first sync
        val grown = snapshot.copy(items = snapshot.items.map { it.copy(weight = it.weight) } + List(9) { SprintItem(19.0) })
        val hour = Math.floorDiv(Instant.parse("2026-10-05T08:00:00Z").epochSecond, 3600L)
        val history = mapOf(hour to com.ynks.burndown.domain.HourPoint(total = 15.0, completed = 0.0))
        for ((type, name) in listOf(ChartType.BURNDOWN to "burndown", ChartType.BURNUP to "burnup")) {
            val series = SprintSeriesBuilder.build(grown, history, now, utc, Resolution.DAILY)
            assertEquals(series.endTotal, series.startRemaining + series.startCompleted, 0.0)
            save(SprintChartRenderer.render(series, type, light, 960, 480, 3f), "$name-scope-growth")
        }
    }
}
