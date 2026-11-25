package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** What a stretch of the countdown ring is expected to be. */
enum class CountdownPhase {
    /** Period, or withdrawal bleed during the pill pause. */
    BLEED,
    FOLLICULAR,
    /** Estimated around ovulation. Never a fertile window: see the product non-goals. */
    OVULATION,
    LUTEAL,
    /** Active pill days. */
    PILL_ACTIVE
}

data class CountdownSegment(val phase: CountdownPhase, val firstDay: Int, val lastDay: Int)

/** Where the ring length comes from, so the UI can say how sure it is. */
enum class CountdownBasis {
    /** Pill pack: active + pause days. Exact, not an estimate. */
    PILL_PACK,
    /** The user's own recorded cycle lengths. */
    PERSONAL,
    /** No completed cycle yet: a typical 28-day cycle, clearly labelled as such. */
    TYPICAL
}

/**
 * The ring on the Today screen: a countdown from the start of the last period
 * (day 1) to the next expected one, split into what will probably happen.
 *
 * @property dayIndex today's day, 1-based. May exceed [length] when the next
 *   period is late; the ring then grows to fit rather than wrapping around.
 */
data class CycleCountdown(
    val anchor: LocalDate,
    val length: Int,
    val dayIndex: Int,
    val nextStart: LocalDate,
    val basis: CountdownBasis,
    val segments: List<CountdownSegment>
) {
    val daysUntilNext: Int get() = length - dayIndex + 1
    val isLate: Boolean get() = dayIndex > length

    fun phaseOn(day: Int): CountdownPhase? = segments.firstOrNull { day in it.firstDay..it.lastDay }?.phase
}

/**
 * Everything the countdown ring draws, by ring day (1 = [CycleCountdown.anchor]).
 * Built on the tracker's device; the Duo partner receives the same shape.
 */
data class CycleRingData(
    val countdown: CycleCountdown,
    val flowByDay: Map<Int, BleedingIntensity>,
    val estimatedDays: Set<Int>,
    /** The personal estimate window, only for [CountdownBasis.PERSONAL]. */
    val estimateWindow: ClosedRange<LocalDate>? = null
)

object CycleRingDataBuilder {
    fun build(
        today: LocalDate,
        countdown: CycleCountdown,
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        estimate: CycleEstimate?
    ): CycleRingData {
        val anchor = countdown.anchor
        val day = countdown.dayIndex

        fun recordedIndex(date: LocalDate): Int? {
            val idx = ChronoUnit.DAYS.between(anchor, date).toInt() + 1
            return idx.takeIf { it in 1..day }
        }

        val flow = mutableMapOf<Int, BleedingIntensity>()
        cycles.flatMap { it.periodDays }.forEach { pd ->
            recordedIndex(pd.date)?.let { flow[it] = pd.bleedingIntensity }
        }
        entries.forEach { entry ->
            val intensity = entry.bleedingIntensity ?: return@forEach
            recordedIndex(entry.date)?.let { flow[it] = intensity }
        }
        flow.values.removeAll { it == BleedingIntensity.NONE }

        // The estimate window, where it falls inside this ring. A pill pack
        // has no window: the pause date is known.
        val window = estimate?.takeIf { countdown.basis == CountdownBasis.PERSONAL }
            ?.let { it.earliestDate..it.latestDate }
        val estimated = if (window != null) {
            (1..maxOf(countdown.length, day)).filter { d ->
                val date = anchor.plusDays((d - 1).toLong())
                date.isAfter(today) && date in window && d !in flow
            }.toSet()
        } else {
            emptySet()
        }
        return CycleRingData(countdown, flow, estimated, window)
    }
}

object CycleCountdownCalculator {
    const val TYPICAL_CYCLE_DAYS = 28
    const val TYPICAL_PERIOD_DAYS = 5

    fun evaluate(
        today: LocalDate,
        currentCycle: Cycle?,
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        estimate: CycleEstimate?,
        pillConfig: PillRegimenConfig
    ): CycleCountdown? {
        if (PillRegimenCalculator.evaluate(pillConfig, today) !is PillDayStatus.Disabled) {
            pillCountdown(today, pillConfig)?.let { return it }
        }
        return naturalCountdown(today, currentCycle, cycles, entries, estimate)
    }

    /**
     * Ring from the start of the pill pause (when the withdrawal bleed is
     * expected) to the next one. Before the first pause, the ring starts at
     * the pause the first pack would have had.
     */
    private fun pillCountdown(today: LocalDate, config: PillRegimenConfig): CycleCountdown? {
        val start = config.startDate ?: return null
        val active = config.activeDays.coerceIn(PillRegimenCalculator.MIN_ACTIVE_DAYS, PillRegimenCalculator.MAX_ACTIVE_DAYS)
        val pause = config.pauseDays.coerceIn(PillRegimenCalculator.MIN_PAUSE_DAYS, PillRegimenCalculator.MAX_PAUSE_DAYS)
        // Continuous use has no pause and so no expected bleed to count down to.
        if (pause == 0) return null
        val packLength = active + pause
        // Pause starts: start + active + k * packLength. Pick the latest one <= today.
        val firstPause = start.plusDays(active.toLong())
        val sinceFirstPause = ChronoUnit.DAYS.between(firstPause, today)
        val packsSince = Math.floorDiv(sinceFirstPause, packLength.toLong())
        val anchor = firstPause.plusDays(packsSince * packLength)
        val day = ChronoUnit.DAYS.between(anchor, today).toInt() + 1
        return CycleCountdown(
            anchor = anchor,
            length = packLength,
            dayIndex = day,
            nextStart = anchor.plusDays(packLength.toLong()),
            basis = CountdownBasis.PILL_PACK,
            segments = listOf(
                CountdownSegment(CountdownPhase.BLEED, 1, pause),
                CountdownSegment(CountdownPhase.PILL_ACTIVE, pause + 1, packLength)
            )
        )
    }

    private fun naturalCountdown(
        today: LocalDate,
        currentCycle: Cycle?,
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        estimate: CycleEstimate?
    ): CycleCountdown? {
        val cycle = currentCycle?.takeIf { !today.isBefore(it.startDate) } ?: return null
        val day = ChronoUnit.DAYS.between(cycle.startDate, today).toInt() + 1

        val personalLength = estimate
            ?.let { ChronoUnit.DAYS.between(cycle.startDate, it.centralDate).toInt() }
            ?.takeIf { it in CycleEstimateCalculator.plausibleCycleDays }
        val length = personalLength ?: TYPICAL_CYCLE_DAYS
        val basis = if (personalLength != null) CountdownBasis.PERSONAL else CountdownBasis.TYPICAL

        val periodDays = averagePeriodLength(cycles, entries)
            ?.coerceIn(2, length / 3)
            ?: TYPICAL_PERIOD_DAYS
        // Same convention as the calendar's phase estimate: luteal phase is
        // the last ~14 days, ovulation the two days before it.
        val lutealStart = maxOf(periodDays + 3, length - 13)
        val ovulationStart = maxOf(periodDays + 1, lutealStart - 2)
        val segments = buildList {
            add(CountdownSegment(CountdownPhase.BLEED, 1, periodDays))
            if (ovulationStart > periodDays + 1) {
                add(CountdownSegment(CountdownPhase.FOLLICULAR, periodDays + 1, ovulationStart - 1))
            }
            add(CountdownSegment(CountdownPhase.OVULATION, ovulationStart, lutealStart - 1))
            add(CountdownSegment(CountdownPhase.LUTEAL, lutealStart, length))
        }
        return CycleCountdown(
            anchor = cycle.startDate,
            length = length,
            dayIndex = day,
            nextStart = cycle.startDate.plusDays(length.toLong()),
            basis = basis,
            segments = segments
        )
    }

    /** Mean length of the first flow run of each recorded cycle, or null without data. */
    private fun averagePeriodLength(cycles: List<Cycle>, entries: List<DailyEntry>): Int? {
        val flowDates = entries.filter { it.bleedingIntensity?.isPeriodFlow() == true }.map { it.date }.toSet() +
            cycles.flatMap { c -> c.periodDays.filter { it.bleedingIntensity.isPeriodFlow() }.map { it.date } }
        val runs = cycles.mapNotNull { cycle ->
            var d = cycle.startDate
            var n = 0
            // Allow the start day itself to lack an entry (marked start, flow logged from day 2).
            while (d in flowDates || (n == 0 && d == cycle.startDate)) {
                n++
                d = d.plusDays(1)
                if (n > 14) break
            }
            n.takeIf { it >= 2 }
        }
        return runs.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }
}
