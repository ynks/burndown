package com.ynks.burndown.domain

import java.time.LocalDate

object CurrentIteration {
    fun pick(iterations: List<Iteration>, today: LocalDate): Iteration? =
        iterations.firstOrNull { today >= it.start && today < it.endExclusive }

    fun nextUpcoming(iterations: List<Iteration>, today: LocalDate): Iteration? =
        iterations.filter { it.start > today }.minByOrNull { it.start }
}
