package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SharedHistoryMapperTest {

    private val today = LocalDate.of(2026, 11, 24)

    private val cycles = listOf(
        // Outside a 3-month window: must never be sent.
        Cycle(id = "old", startDate = LocalDate.of(2026, 7, 2)),
        Cycle(id = "c1", startDate = LocalDate.of(2026, 10, 16)),
        Cycle(id = "c2", startDate = LocalDate.of(2026, 11, 11))
    )

    private val entries = listOf(
        DailyEntry(date = LocalDate.of(2026, 7, 2), bleedingIntensity = BleedingIntensity.HEAVY, moodLevel = 2),
        DailyEntry(
            date = LocalDate.of(2026, 11, 11),
            bleedingIntensity = BleedingIntensity.MEDIUM,
            painLevel = 4,
            moodLevel = 3,
            energyLevel = 2,
            symptomIds = setOf("cramps"),
            notes = "private"
        ),
        DailyEntry(date = LocalDate.of(2026, 11, 12), bleedingIntensity = BleedingIntensity.HEAVY),
        DailyEntry(date = LocalDate.of(2026, 11, 20), bleedingIntensity = BleedingIntensity.NONE, moodLevel = 4)
    )

    private val estimate = CycleEstimate(
        earliestDate = LocalDate.of(2026, 12, 6),
        centralDate = LocalDate.of(2026, 12, 9),
        latestDate = LocalDate.of(2026, 12, 12),
        cycleCount = 3,
        variabilityDays = 2
    )

    private fun compose(sharing: DuoSharingPreferences, pill: PillRegimenConfig = PillRegimenConfig()) =
        SharedHistoryMapper.compose(today, sharing, cycles, entries, pill, estimate)

    @Test
    fun `nothing is composed when no history grant is on`() {
        assertNull(compose(DuoSharingPreferences(shareMood = true, sharePeriodEstimate = true)))
    }

    @Test
    fun `only granted categories are present`() {
        val history = compose(DuoSharingPreferences(shareHistoryFlow = true, historyMonths = 3))!!

        assertNotNull(history.flow)
        assertNotNull(history.cycleStarts)
        assertNull(history.levels)
        assertNull(history.estimate)
        assertNull(history.pill)
    }

    @Test
    fun `flow and cycle starts stay inside the window and skip NONE`() {
        val history = compose(DuoSharingPreferences(shareHistoryFlow = true, historyMonths = 3))!!

        assertEquals("2026-08-24", history.from)
        assertEquals("2026-11-24", history.to)
        assertEquals(listOf("2026-10-16", "2026-11-11"), history.cycleStarts)
        assertEquals(
            listOf(SharedFlowDay("2026-11-11", "medium"), SharedFlowDay("2026-11-12", "heavy")),
            history.flow
        )
    }

    @Test
    fun `levels carry only mood and energy`() {
        val history = compose(DuoSharingPreferences(shareHistoryLevels = true, historyMonths = 3))!!

        assertEquals(
            listOf(
                SharedDayLevels("2026-11-11", mood = 3, energy = 2),
                SharedDayLevels("2026-11-20", mood = 4, energy = null)
            ),
            history.levels
        )
        assertNull(history.flow)
    }

    @Test
    fun `invalid month setting falls back to the default`() {
        val history = compose(DuoSharingPreferences(shareHistoryLevels = true, historyMonths = 7))!!
        assertEquals(DuoSharingPreferences.DEFAULT_HISTORY_MONTHS, history.months)
    }

    @Test
    fun `pill anchor is the pack containing the window start, not the regimen start`() {
        val regimenStart = LocalDate.of(2026, 1, 1)
        val pill = PillRegimenConfig(
            isEnabled = true,
            activeDays = 21,
            pauseDays = 7,
            startDate = regimenStart,
            takenDates = setOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 23))
        )
        val history = compose(DuoSharingPreferences(shareHistoryPill = true, historyMonths = 3), pill)!!
        val shared = history.pill!!

        val anchor = LocalDate.parse(shared.anchor)
        val from = LocalDate.parse(history.from)
        assertTrue(!anchor.isAfter(from) && anchor.plusDays(28).isAfter(from))
        // Same pack phase as the original regimen on every day.
        assertEquals(
            PillRegimenCalculator.evaluate(pill.copy(takenDates = emptySet()), today),
            PillRegimenCalculator.evaluate(PillRegimenConfig(true, 21, 7, anchor), today)
        )
        assertEquals(listOf("2026-11-23"), shared.takenDates)
    }

    @Test
    fun `partner side rebuilds calendar inputs from the shared history`() {
        val history = compose(
            DuoSharingPreferences(
                shareHistoryFlow = true,
                shareHistoryEstimate = true,
                shareHistoryLevels = true,
                historyMonths = 3
            )
        )!!
        val inputs = SharedHistoryMapper.toCalendarInputs(history)!!

        assertEquals(2, inputs.cycles.size)
        val nov11 = inputs.entries.first { it.date == LocalDate.of(2026, 11, 11) }
        assertEquals(BleedingIntensity.MEDIUM, nov11.bleedingIntensity)
        assertEquals(3, nov11.moodLevel)
        // Never shared, so never reconstructed.
        assertNull(nov11.painLevel)
        assertTrue(nov11.symptomIds.isEmpty())
        assertEquals("", nov11.notes)
        assertTrue(inputs.estimateResult is CycleEstimateResult.Available)
        assertTrue(!inputs.pillConfig.isEnabled)
    }

    @Test
    fun `malformed history yields no calendar instead of crashing`() {
        assertNull(SharedHistoryMapper.toCalendarInputs(SharedHistory(from = "nope", to = "2026-11-24", months = 6)))
    }
}
