package com.ynks.burndown.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SeriesPoint(val time: Instant, val total: Double, val completed: Double) {
    val remaining: Double get() = (total - completed).coerceAtLeast(0.0)
}

enum class Trend { UP, DOWN, FLAT }

data class SprintStats(
    val remaining: Double,
    val completed: Double,
    val total: Double,
    val daysLeft: Int,
    val requiredPerDay: Double?,
    val trend: Trend?,
    val trendDelta: Double?,
)

data class SprintSeries(
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val resolution: Resolution,
    val points: List<SeriesPoint>,
    val dayStarts: List<Instant>,
    val startRemaining: Double,
    val startCompleted: Double,
    val endTotal: Double,
    val stats: SprintStats,
) {
    val last: SeriesPoint? get() = points.lastOrNull()

    fun fraction(time: Instant): Double {
        val span = end.toEpochMilli() - start.toEpochMilli()
        if (span <= 0) return 0.0
        return ((time.toEpochMilli() - start.toEpochMilli()).toDouble() / span).coerceIn(0.0, 1.0)
    }

    fun idealBurndownAt(time: Instant): Double = startRemaining * (1.0 - fraction(time))

    fun idealBurnupAt(time: Instant): Double = startCompleted + (endTotal - startCompleted) * fraction(time)

    val aheadBy: Double
        get() = last?.let { idealBurndownAt(it.time) - it.remaining } ?: 0.0
}

val SprintSeries.totalDays: Int
    get() = ChronoUnit.DAYS.between(start.atZone(zone).toLocalDate(), end.atZone(zone).toLocalDate()).toInt()

fun SprintSeries.dayNumber(today: LocalDate = LocalDate.now(zone)): Int =
    ChronoUnit.DAYS.between(start.atZone(zone).toLocalDate(), today).toInt() + 1
