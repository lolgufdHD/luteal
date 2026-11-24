package fr.luteal.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the partner's read-only calendar is built from. */
data class PartnerCalendarInputs(
    val from: LocalDate,
    val to: LocalDate,
    val months: Int,
    val cycles: List<Cycle>,
    val entries: List<DailyEntry>,
    val estimateResult: CycleEstimateResult,
    val pillConfig: PillRegimenConfig
)

/**
 * Converts between the tracker's journal and the grant-filtered
 * [SharedHistory] sealed into the Duo projection.
 *
 * [compose] runs on the tracker's device and is the only place that decides
 * what leaves it: a category that is not granted is never read into the
 * result. [toCalendarInputs] runs on the partner's device and rebuilds just
 * enough domain data to reuse [MonthCalendarProjectionCalculator].
 */
object SharedHistoryMapper {

    fun compose(
        today: LocalDate,
        sharing: DuoSharingPreferences,
        cycles: List<Cycle>,
        entries: List<DailyEntry>,
        pillConfig: PillRegimenConfig,
        estimate: CycleEstimate?
    ): SharedHistory? {
        if (!sharing.sharesAnyHistory) return null
        val months = DuoSharingPreferences.normalizeHistoryMonths(sharing.historyMonths)
        val from = today.minusMonths(months.toLong())
        fun inWindow(date: LocalDate) = !date.isBefore(from) && !date.isAfter(today)

        val (cycleStarts, flow) = if (sharing.shareHistoryFlow) {
            val byDate = mutableMapOf<LocalDate, BleedingIntensity>()
            cycles.flatMap { it.periodDays }.forEach { byDate[it.date] = it.bleedingIntensity }
            entries.forEach { entry -> entry.bleedingIntensity?.let { byDate[entry.date] = it } }
            val shared = byDate
                .filter { (date, intensity) -> inWindow(date) && intensity != BleedingIntensity.NONE }
                .toSortedMap()
                .map { (date, intensity) -> SharedFlowDay(date.toString(), intensity.name.lowercase()) }
            val starts = cycles.map { it.startDate }.filter(::inWindow).distinct().sorted().map { it.toString() }
            starts to shared
        } else {
            null to null
        }

        val sharedEstimate = if (sharing.shareHistoryEstimate) {
            estimate?.let {
                SharedEstimate(
                    windowStart = it.earliestDate.toString(),
                    windowEnd = it.latestDate.toString(),
                    centralDate = it.centralDate.toString(),
                    cycleCount = it.cycleCount,
                    variabilityDays = it.variabilityDays
                )
            }
        } else {
            null
        }

        val levels = if (sharing.shareHistoryLevels) {
            entries
                .filter { inWindow(it.date) && (it.moodLevel != null || it.energyLevel != null) }
                .sortedBy { it.date }
                .map { SharedDayLevels(it.date.toString(), it.moodLevel, it.energyLevel) }
        } else {
            null
        }

        val pill = if (sharing.shareHistoryPill) composePill(pillConfig, from, today) else null

        return SharedHistory(
            from = from.toString(),
            to = today.toString(),
            months = months,
            cycleStarts = cycleStarts,
            flow = flow,
            estimate = sharedEstimate,
            levels = levels,
            pill = pill
        )
    }

    private fun composePill(config: PillRegimenConfig, from: LocalDate, today: LocalDate): SharedPillHistory? {
        val start = config.startDate ?: return null
        if (!config.isEnabled || start.isAfter(today)) return null
        val active = config.activeDays.coerceIn(PillRegimenCalculator.MIN_ACTIVE_DAYS, PillRegimenCalculator.MAX_ACTIVE_DAYS)
        val pause = config.pauseDays.coerceIn(PillRegimenCalculator.MIN_PAUSE_DAYS, PillRegimenCalculator.MAX_PAUSE_DAYS)
        val packLength = (active + pause).toLong()
        // Share the start of the pack that contains `from`, never the original
        // regimen start date, which may lie far outside the granted window.
        val anchor = if (start.isBefore(from)) {
            val packsBefore = ChronoUnit.DAYS.between(start, from) / packLength
            start.plusDays(packsBefore * packLength)
        } else {
            start
        }
        val taken = buildSet {
            addAll(config.takenDates)
            config.lastTakenDate?.let { add(it) }
        }
            .filter { !it.isBefore(from) && !it.isAfter(today) }
            .sorted()
            .map { it.toString() }
        return SharedPillHistory(
            anchor = anchor.toString(),
            activeDays = active,
            pauseDays = pause,
            takenDates = taken
        )
    }

    /** Null when the history is malformed; the partner then simply sees no calendar. */
    fun toCalendarInputs(history: SharedHistory): PartnerCalendarInputs? = runCatching {
        val from = LocalDate.parse(history.from)
        val to = LocalDate.parse(history.to)

        val cycles = history.cycleStarts.orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .distinct()
            .sorted()
            .map { Cycle(id = "shared-$it", startDate = it) }

        val flowByDate = history.flow.orEmpty().mapNotNull { day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull() ?: return@mapNotNull null
            val intensity = BleedingIntensity.entries.firstOrNull { it.name.equals(day.intensity, ignoreCase = true) }
                ?: return@mapNotNull null
            date to intensity
        }.toMap()
        val levelsByDate = history.levels.orEmpty().mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day }
        }.toMap()

        val entries = (flowByDate.keys + levelsByDate.keys).sorted().mapNotNull { date ->
            val levels = levelsByDate[date]
            runCatching {
                DailyEntry(
                    date = date,
                    bleedingIntensity = flowByDate[date],
                    moodLevel = levels?.mood?.takeIf { it in DailyEntry.TRACKING_SCALE },
                    energyLevel = levels?.energy?.takeIf { it in DailyEntry.TRACKING_SCALE },
                    updatedAt = Instant.EPOCH
                )
            }.getOrNull()
        }

        val estimateResult = history.estimate?.let { shared ->
            runCatching {
                CycleEstimateResult.Available(
                    CycleEstimate(
                        earliestDate = LocalDate.parse(shared.windowStart),
                        centralDate = LocalDate.parse(shared.centralDate ?: shared.windowStart),
                        latestDate = LocalDate.parse(shared.windowEnd),
                        cycleCount = shared.cycleCount ?: 0,
                        variabilityDays = shared.variabilityDays ?: 0
                    )
                )
            }.getOrNull()
        } ?: CycleEstimateResult.NeedsMoreHistory

        val pillConfig = history.pill?.let { pill ->
            PillRegimenConfig(
                isEnabled = true,
                activeDays = pill.activeDays,
                pauseDays = pill.pauseDays,
                startDate = LocalDate.parse(pill.anchor),
                takenDates = pill.takenDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            )
        } ?: PillRegimenConfig()

        PartnerCalendarInputs(
            from = from,
            to = to,
            months = history.months,
            cycles = cycles,
            entries = entries,
            estimateResult = estimateResult,
            pillConfig = pillConfig
        )
    }.getOrNull()
}
