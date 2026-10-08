package com.ynks.burndown.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CurrentIterationTest {
    private val s1 = Iteration("1", "S1", "2026-09-21", 14)
    private val s2 = Iteration("2", "S2", "2026-10-05", 14)
    private val s3 = Iteration("3", "S3", "2026-10-19", 14)
    private val all = listOf(s3, s1, s2)

    @Test fun picksTheIterationContainingToday() {
        assertEquals(s2, CurrentIteration.pick(all, LocalDate.parse("2026-10-08")))
    }

    @Test fun firstAndLastDaysAreInclusiveAndEndIsExclusive() {
        assertEquals(s2, CurrentIteration.pick(all, LocalDate.parse("2026-10-05")))
        assertEquals(s2, CurrentIteration.pick(all, LocalDate.parse("2026-10-18")))
        assertEquals(s3, CurrentIteration.pick(all, LocalDate.parse("2026-10-19")))
    }

    @Test fun noneWhenOutsideEveryIteration() {
        assertNull(CurrentIteration.pick(all, LocalDate.parse("2026-12-01")))
        assertEquals(s3, CurrentIteration.nextUpcoming(listOf(s1, s3), LocalDate.parse("2026-10-10")))
    }
}
