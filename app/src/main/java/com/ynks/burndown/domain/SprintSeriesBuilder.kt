package com.ynks.burndown.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

object SprintSeriesBuilder {

    fun build(
        snapshot: SprintSnapshot,
        history: Map<Long, HourPoint>,
        now: Instant,
        zone: ZoneId,
        resolution: Resolution,
    ): SprintSeries {
        val iteration = snapshot.iteration
        val start = iteration.start.atStartOfDay(zone).toInstant()
        val end = iteration.endExclusive.atStartOfDay(zone).toInstant()

        val dayStarts = (0 until iteration.durationDays).map {
            iteration.start.plusDays(it.toLong()).atStartOfDay(zone).toInstant()
        }
        val boundaries = boundaries(start, end, dayStarts, resolution)

        val currentTotal = snapshot.items.sumOf { it.weight }
        val doneTimes = snapshot.items.filter { it.doneAt != null }
            .map { Instant.ofEpochMilli(it.doneAt!!) to it.weight }
        fun completedAt(t: Instant) = doneTimes.sumOf { (done, w) -> if (done <= t) w else 0.0 }

        val snapshots = history.entries.sortedBy { it.key }
        fun totalAt(t: Instant): Double {
            // last sync before t or else the earliest one we have
            val before = snapshots.lastOrNull { hourStart(it.key) < t }
            if (before != null) return before.value.total
            return snapshots.firstOrNull()?.value?.total ?: currentTotal
        }

        val points = ArrayList<SeriesPoint>()
        if (now >= start) {
            for (k in boundaries.indices) {
                val boundary = boundaries[k]
                val known = k == 0 || boundaries[k - 1] <= now
                if (!known) break
                val t = if (boundary > now) now else boundary
                val live = t == now
                val total = if (live) currentTotal else maxOf(totalAt(t), completedAt(t))
                points += SeriesPoint(t, total, completedAt(t).coerceAtMost(total))
                if (live) break
            }
        }

        val first = points.firstOrNull()
        val startCompleted = first?.completed ?: completedAt(start)
        // anchor the ideal line on the peak scope
        val peakTotal = max(points.maxOfOrNull { it.total } ?: currentTotal, currentTotal)

        fun daysLeftAt(t: Instant): Int {
            if (t >= end) return 0
            val elapsed = ChronoUnit.DAYS.between(iteration.start, t.atZone(zone).toLocalDate()).toInt()
            return (iteration.durationDays - elapsed).coerceIn(0, iteration.durationDays)
        }
        fun required(total: Double, completed: Double, t: Instant): Double? {
            val left = daysLeftAt(t)
            return if (left > 0) (total - completed).coerceAtLeast(0.0) / left else null
        }

        val nowCompleted = completedAt(now).coerceAtMost(currentTotal)
        val requiredNow = required(currentTotal, nowCompleted, now)
        val before = now.minus(Duration.ofHours(24))
        val requiredBefore = if (before >= start) {
            val c = completedAt(before)
            required(max(totalAt(before), c), c, before)
        } else {
            null
        }
        val trend = if (requiredNow != null && requiredBefore != null) {
            val change = (requiredNow - requiredBefore) / max(requiredBefore, 1e-9)
            when {
                requiredNow == requiredBefore || abs(change) < 0.01 -> Trend.FLAT
                change > 0 -> Trend.UP
                else -> Trend.DOWN
            }
        } else {
            null
        }

        return SprintSeries(
            start = start,
            end = end,
            zone = zone,
            resolution = resolution,
            points = points,
            dayStarts = dayStarts,
            startRemaining = (peakTotal - startCompleted).coerceAtLeast(0.0),
            startCompleted = startCompleted,
            endTotal = currentTotal,
            stats = SprintStats(
                remaining = (currentTotal - nowCompleted).coerceAtLeast(0.0),
                completed = nowCompleted,
                total = currentTotal,
                daysLeft = daysLeftAt(now),
                requiredPerDay = requiredNow,
                trend = trend,
                trendDelta = if (requiredNow != null && requiredBefore != null) requiredNow - requiredBefore else null,
            ),
        )
    }

    private fun hourStart(epochHour: Long): Instant = Instant.ofEpochSecond(epochHour * 3600L)

    private fun boundaries(
        start: Instant,
        end: Instant,
        dayStarts: List<Instant>,
        resolution: Resolution,
    ): List<Instant> = when (resolution) {
        Resolution.DAILY -> listOf(start) + dayStarts.drop(1) + end
        Resolution.HOURLY -> {
            // absolute hours so dst days get the right bucket count
            val hours = Duration.between(start, end).toHours().coerceAtLeast(1)
            val steps = (0..hours).map { start.plus(Duration.ofHours(it)) }.toMutableList()
            if (steps.last() < end) steps += end
            steps
        }
    }
}
