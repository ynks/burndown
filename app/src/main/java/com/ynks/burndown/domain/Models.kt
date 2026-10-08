package com.ynks.burndown.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class Resolution { DAILY, HOURLY }

enum class ChartType { BURNDOWN, BURNUP }

enum class Metric { COUNT, POINTS }

@Serializable
data class Iteration(
    val id: String,
    val title: String,
    val startDate: String,
    val durationDays: Int,
) {
    val start: LocalDate get() = LocalDate.parse(startDate)
    val endExclusive: LocalDate get() = start.plusDays(durationDays.toLong())
}

@Serializable
data class SprintItem(val weight: Double, val doneAt: Long? = null)

@Serializable
data class SprintSnapshot(
    val iteration: Iteration,
    val items: List<SprintItem>,
    val fetchedAt: Long,
    val metricKey: String,
)

@Serializable
data class HourPoint(val total: Double, val completed: Double)
