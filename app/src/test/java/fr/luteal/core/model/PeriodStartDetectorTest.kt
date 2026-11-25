package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodStartDetectorTest {

    private fun flow(vararg days: String, intensity: BleedingIntensity = BleedingIntensity.MEDIUM) =
        days.map { DailyEntry(date = LocalDate.parse(it), bleedingIntensity = intensity) }

    @Test
    fun `flow logged without a period start becomes a new start`() {
        // The reported case: cycle marked 16 Oct, flow 12-18 Nov never marked.
        val cycles = listOf(Cycle(id = "oct", startDate = LocalDate.of(2026, 10, 16)))
        val entries = flow("2026-11-12", "2026-11-13", "2026-11-14", "2026-11-18")

        assertEquals(listOf(LocalDate.of(2026, 11, 12)), PeriodStartDetector.missingStarts(cycles, entries))
    }

    @Test
    fun `flow inside an already started period adds nothing`() {
        val cycles = listOf(Cycle(id = "nov", startDate = LocalDate.of(2026, 11, 12)))
        val entries = flow("2026-11-12", "2026-11-13", "2026-11-14")

        assertTrue(PeriodStartDetector.missingStarts(cycles, entries).isEmpty())
    }

    @Test
    fun `a start marked on day two of the run still covers it`() {
        val cycles = listOf(Cycle(id = "nov", startDate = LocalDate.of(2026, 11, 13)))
        val entries = flow("2026-11-12", "2026-11-13")

        assertTrue(PeriodStartDetector.missingStarts(cycles, entries).isEmpty())
    }

    @Test
    fun `flow too soon after a start is not a new cycle`() {
        val cycles = listOf(Cycle(id = "nov", startDate = LocalDate.of(2026, 11, 1)))
        val entries = flow("2026-11-10", "2026-11-11")

        assertTrue(PeriodStartDetector.missingStarts(cycles, entries).isEmpty())
    }

    @Test
    fun `spotting never starts a cycle`() {
        val entries = flow("2026-11-12", "2026-11-13", intensity = BleedingIntensity.SPOTTING)

        assertTrue(PeriodStartDetector.missingStarts(emptyList(), entries).isEmpty())
    }

    @Test
    fun `several unmarked periods each get a start`() {
        val entries = flow("2026-09-01", "2026-09-02", "2026-09-29", "2026-09-30", "2026-10-27")

        assertEquals(
            listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 27)),
            PeriodStartDetector.missingStarts(emptyList(), entries)
        )
    }

    @Test
    fun `one dry day inside a period keeps it one run`() {
        val entries = flow("2026-11-12", "2026-11-14")

        assertEquals(listOf(LocalDate.of(2026, 11, 12)), PeriodStartDetector.missingStarts(emptyList(), entries))
    }
}
