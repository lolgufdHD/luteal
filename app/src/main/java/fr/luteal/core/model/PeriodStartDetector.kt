package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Finds period starts that were logged as flow days but never marked as a
 * cycle start.
 *
 * Cycles are only created when the user explicitly taps "log period start".
 * Someone who simply records light/medium/heavy flow in the daily editor
 * otherwise stays in the previous cycle forever: the cycle day keeps
 * counting (day 41, 60, ...) and the estimate never moves. A run of period
 * flow is treated as a new start when no recorded start lies inside the run
 * or in the [MIN_DAYS_BETWEEN_STARTS] days before it (that would be the same
 * period, or one too close to be a new cycle).
 *
 * Spotting never starts a cycle.
 */
object PeriodStartDetector {
    /** Shortest gap between two starts that still counts as a new cycle. */
    const val MIN_DAYS_BETWEEN_STARTS = 14

    /** Flow days at most this far apart belong to the same run (one dry day allowed). */
    private const val MAX_GAP_INSIDE_RUN = 2L

    fun missingStarts(cycles: List<Cycle>, entries: List<DailyEntry>): List<LocalDate> {
        val flowDates = entries
            .filter { it.bleedingIntensity?.isPeriodFlow() == true }
            .map { it.date }
            .distinct()
            .sorted()
        if (flowDates.isEmpty()) return emptyList()

        val runs = mutableListOf<ClosedRange<LocalDate>>()
        var runStart = flowDates.first()
        var runEnd = runStart
        for (date in flowDates.drop(1)) {
            if (ChronoUnit.DAYS.between(runEnd, date) <= MAX_GAP_INSIDE_RUN) {
                runEnd = date
            } else {
                runs += runStart..runEnd
                runStart = date
                runEnd = date
            }
        }
        runs += runStart..runEnd

        val starts = cycles.map { it.startDate }.toMutableSet()
        val missing = mutableListOf<LocalDate>()
        for (run in runs) {
            val earliestCovering = run.start.minusDays((MIN_DAYS_BETWEEN_STARTS - 1).toLong())
            val covered = starts.any { !it.isBefore(earliestCovering) && !it.isAfter(run.endInclusive) }
            if (!covered) {
                missing += run.start
                // Later runs are judged against this new start too.
                starts += run.start
            }
        }
        return missing
    }
}
