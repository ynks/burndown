package com.ynks.burndown.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SprintSeriesBuilderTest {
    private val utc = ZoneId.of("UTC")

    private val iteration = Iteration("it1", "Sprint 12", "2026-10-05", 5)
    private fun at(s: String) = Instant.parse(s)
    private fun ms(s: String) = at(s).toEpochMilli()

    private val items = listOf(
        SprintItem(3.0, ms("2026-10-06T10:00:00Z")),
        SprintItem(2.0, null),
        SprintItem(5.0, ms("2026-10-07T09:30:00Z")),
    )
    private val snapshot = SprintSnapshot(iteration, items, ms("2026-10-07T12:00:00Z"), "count")
    private val now = at("2026-10-07T12:00:00Z")

    @Test
    fun dailyPointsStopAtNowLeavingFutureBlank() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.DAILY)

        assertEquals(
            listOf("2026-10-05T00:00:00Z", "2026-10-06T00:00:00Z", "2026-10-07T00:00:00Z", "2026-10-07T12:00:00Z").map(::at),
            series.points.map { it.time },
        )
        assertEquals(now, series.last!!.time)
        assertTrue(series.points.none { it.time > now })
    }

    @Test
    fun completedIsExactAndRemainingDerived() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.DAILY)
        assertEquals(listOf(0.0, 0.0, 3.0, 8.0), series.points.map { it.completed })
        assertEquals(listOf(10.0, 10.0, 7.0, 2.0), series.points.map { it.remaining })
        assertEquals(10.0, series.endTotal, 0.0)
    }

    @Test
    fun trendLinesRunLinearlyAcrossTheWholeSprint() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.DAILY)
        assertEquals(10.0, series.startRemaining, 0.0)
        assertEquals(10.0, series.idealBurndownAt(series.start), 1e-9)
        assertEquals(0.0, series.idealBurndownAt(series.end), 1e-9)
        assertEquals(5.0, series.idealBurndownAt(at("2026-10-07T12:00:00Z")), 1e-9)
        assertEquals(0.0, series.idealBurnupAt(series.start), 1e-9)
        assertEquals(10.0, series.idealBurnupAt(series.end), 1e-9)
        assertEquals(3.0, series.aheadBy, 1e-9)
    }

    @Test
    fun hourlyHasOnePointPerHour() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.HOURLY)
        assertEquals(2 * 24 + 12 + 1, series.points.size)
        assertEquals(series.start, series.points.first().time)
        assertEquals(now, series.last!!.time)
        series.points.zipWithNext().forEach { (a, b) -> assertTrue(b.time > a.time) }
    }

    @Test
    fun hourlyCompletionLandsInTheHourItHappened() {
        val s = snapshot.copy(items = listOf(SprintItem(1.0, ms("2026-10-06T14:20:00Z")), SprintItem(1.0)))
        val series = SprintSeriesBuilder.build(s, emptyMap(), now, utc, Resolution.HOURLY)
        fun completedAt(t: String) = series.points.first { it.time == at(t) }.completed
        assertEquals(0.0, completedAt("2026-10-06T14:00:00Z"), 0.0)
        assertEquals(1.0, completedAt("2026-10-06T15:00:00Z"), 0.0)
    }

    @Test
    fun dailyMatchesHourlySampledAtDayEnds() {
        val daily = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.DAILY)
        val hourly = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.HOURLY)
        daily.points.forEach { d ->
            val h = hourly.points.first { it.time == d.time }
            assertEquals(d.completed, h.completed, 0.0)
            assertEquals(d.total, h.total, 0.0)
        }
    }

    @Test
    fun scopeChangesComeFromRecordedHistory() {
        fun hour(s: String) = Math.floorDiv(at(s).epochSecond, 3600L)
        val history = mapOf(
            hour("2026-10-05T08:00:00Z") to HourPoint(total = 5.0, completed = 0.0),
            hour("2026-10-06T12:00:00Z") to HourPoint(total = 8.0, completed = 3.0),
        )
        val series = SprintSeriesBuilder.build(snapshot, history, now, utc, Resolution.DAILY)
        assertEquals(5.0, series.points[0].total, 0.0)
        assertEquals(5.0, series.points[1].total, 0.0)
        assertEquals(8.0, series.points[2].total, 0.0)
        assertEquals(10.0, series.points[3].total, 0.0)
        assertEquals(10.0, series.startRemaining, 0.0)
    }

    @Test
    fun workDoneBeforeSprintStartCountsFromTheFirstPoint() {
        val s = snapshot.copy(items = listOf(SprintItem(2.0, ms("2026-09-30T10:00:00Z")), SprintItem(2.0)))
        val series = SprintSeriesBuilder.build(s, emptyMap(), now, utc, Resolution.DAILY)
        assertEquals(2.0, series.points.first().completed, 0.0)
        assertEquals(2.0, series.startRemaining, 0.0)
        assertEquals(2.0, series.startCompleted, 0.0)
    }

    @Test
    fun beforeTheSprintStartsThereAreNoPoints() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), at("2026-10-01T00:00:00Z"), utc, Resolution.DAILY)
        assertTrue(series.points.isEmpty())
        assertEquals(0.0, series.aheadBy, 0.0)
    }

    @Test
    fun afterTheSprintEndsTheSeriesStopsAtTheEnd() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), at("2026-11-01T00:00:00Z"), utc, Resolution.DAILY)
        assertEquals(6, series.points.size)
        assertEquals(series.end, series.last!!.time)
    }

    @Test
    fun dayBoundariesFollowTheTimeZone() {
        val berlin = ZoneId.of("Europe/Berlin")
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, berlin, Resolution.DAILY)
        assertEquals(at("2026-10-04T22:00:00Z"), series.start)
        assertEquals(5, series.dayStarts.size)
    }

    @Test
    fun hourlyBucketsAbsorbDaylightSavingDays() {
        val berlin = ZoneId.of("Europe/Berlin")
        // dst day only has 23 hours
        val spring = SprintSnapshot(Iteration("it2", "S", "2026-03-28", 3), emptyList(), 0, "count")
        val series = SprintSeriesBuilder.build(spring, emptyMap(), at("2026-04-30T00:00:00Z"), berlin, Resolution.HOURLY)
        assertEquals(24 + 23 + 24 + 1, series.points.size)
        assertEquals(series.end, series.last!!.time)
    }

    @Test
    fun idealLineStartsFromThePeakScopeWhenWorkIsAddedLater() {
        fun hour(s: String) = Math.floorDiv(at(s).epochSecond, 3600L)
        val grown = snapshot.copy(items = items + SprintItem(176.0))
        val history = mapOf(hour("2026-10-05T08:00:00Z") to HourPoint(total = 10.0, completed = 0.0))
        val series = SprintSeriesBuilder.build(grown, history, now, utc, Resolution.DAILY)
        assertEquals(186.0, series.startRemaining, 0.0)
        assertEquals(186.0, series.idealBurndownAt(series.start), 1e-9)
        assertEquals(0.0, series.idealBurndownAt(series.end), 1e-9)
        assertEquals(93.0, series.idealBurndownAt(at("2026-10-07T12:00:00Z")), 1e-9)
    }

    @Test
    fun statsGiveTheVelocityNeededToFinishAndItsTrend() {
        val series = SprintSeriesBuilder.build(snapshot, emptyMap(), now, utc, Resolution.DAILY)
        val stats = series.stats
        assertEquals(3, stats.daysLeft)
        assertEquals(2.0, stats.remaining, 0.0)
        assertEquals(10.0, stats.total, 0.0)
        assertEquals(2.0 / 3, stats.requiredPerDay!!, 1e-9)
        assertEquals(Trend.DOWN, stats.trend)
        assertEquals(2.0 / 3 - 1.75, stats.trendDelta!!, 1e-9)
    }

    @Test
    fun trendIsUpWhenWorkIsAddedAndAbsentOnTheFirstDay() {
        fun hour(s: String) = Math.floorDiv(at(s).epochSecond, 3600L)
        val history = mapOf(
            hour("2026-10-05T08:00:00Z") to HourPoint(total = 10.0, completed = 0.0),
        )
        val grown = snapshot.copy(items = items + SprintItem(40.0))
        val up = SprintSeriesBuilder.build(grown, history, now, utc, Resolution.DAILY).stats
        assertEquals(Trend.UP, up.trend)
        assertEquals(42.0 / 3 - 7.0 / 4, up.trendDelta!!, 1e-9)

        val firstDay = SprintSeriesBuilder.build(snapshot, emptyMap(), at("2026-10-05T10:00:00Z"), utc, Resolution.DAILY).stats
        assertEquals(null, firstDay.trend)
    }

    @Test
    fun noVelocityOnceTheSprintIsOver() {
        val stats = SprintSeriesBuilder.build(snapshot, emptyMap(), at("2026-11-01T00:00:00Z"), utc, Resolution.DAILY).stats
        assertEquals(0, stats.daysLeft)
        assertEquals(null, stats.requiredPerDay)
        assertEquals(null, stats.trend)
    }
}
