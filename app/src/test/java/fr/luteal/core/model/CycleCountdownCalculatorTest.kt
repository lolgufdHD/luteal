package fr.luteal.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleCountdownCalculatorTest {

    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun `pill pack counts down from the pause to the next pause`() {
        // Pack started 18 Nov (day 8 of 21 today); the pause before it ran 11-17 Nov.
        val pill = PillRegimenConfig(isEnabled = true, activeDays = 21, pauseDays = 7, startDate = LocalDate.of(2026, 10, 21))
        val countdown = CycleCountdownCalculator.evaluate(today, null, emptyList(), emptyList(), null, pill)!!

        assertEquals(CountdownBasis.PILL_PACK, countdown.basis)
        assertEquals(LocalDate.of(2026, 11, 11), countdown.anchor)
        assertEquals(28, countdown.length)
        assertEquals(15, countdown.dayIndex)
        assertEquals(LocalDate.of(2026, 12, 9), countdown.nextStart)
        assertEquals(14, countdown.daysUntilNext)
        assertEquals(CountdownPhase.BLEED, countdown.phaseOn(1))
        assertEquals(CountdownPhase.PILL_ACTIVE, countdown.phaseOn(15))
    }

    @Test
    fun `continuous pill use has no countdown of its own`() {
        val pill = PillRegimenConfig(isEnabled = true, activeDays = 28, pauseDays = 0, startDate = LocalDate.of(2026, 10, 1))
        assertNull(CycleCountdownCalculator.evaluate(today, null, emptyList(), emptyList(), null, pill))
    }

    @Test
    fun `natural cycle uses the personal estimate`() {
        val current = Cycle(id = "c", startDate = LocalDate.of(2026, 11, 12))
        val estimate = CycleEstimate(
            earliestDate = LocalDate.of(2026, 12, 7),
            centralDate = LocalDate.of(2026, 12, 12),
            latestDate = LocalDate.of(2026, 12, 18),
            cycleCount = 2,
            variabilityDays = 2
        )
        val countdown = CycleCountdownCalculator.evaluate(today, current, listOf(current), emptyList(), estimate, PillRegimenConfig())!!

        assertEquals(CountdownBasis.PERSONAL, countdown.basis)
        assertEquals(30, countdown.length)
        assertEquals(14, countdown.dayIndex)
        assertEquals(CountdownPhase.BLEED, countdown.phaseOn(1))
        assertEquals(CountdownPhase.LUTEAL, countdown.phaseOn(30))
    }

    @Test
    fun `without a completed cycle a typical 28 days is used and labelled`() {
        val current = Cycle(id = "c", startDate = LocalDate.of(2026, 11, 12))
        val countdown = CycleCountdownCalculator.evaluate(today, current, listOf(current), emptyList(), null, PillRegimenConfig())!!

        assertEquals(CountdownBasis.TYPICAL, countdown.basis)
        assertEquals(28, countdown.length)
    }

    @Test
    fun `segments cover every day exactly once`() {
        val current = Cycle(id = "c", startDate = LocalDate.of(2026, 11, 12))
        for (length in listOf(15, 21, 28, 35, 60)) {
            val estimate = CycleEstimate(
                earliestDate = current.startDate.plusDays(length - 3L),
                centralDate = current.startDate.plusDays(length.toLong()),
                latestDate = current.startDate.plusDays(length + 3L),
                cycleCount = 1,
                variabilityDays = 0
            )
            val countdown = CycleCountdownCalculator.evaluate(today, current, listOf(current), emptyList(), estimate, PillRegimenConfig())!!
            val covered = countdown.segments.flatMap { (it.firstDay..it.lastDay).toList() }
            assertEquals("length $length", (1..length).toList(), covered)
        }
    }

    @Test
    fun `late period is reported instead of wrapping around`() {
        val current = Cycle(id = "c", startDate = LocalDate.of(2026, 10, 16))
        val countdown = CycleCountdownCalculator.evaluate(today, current, listOf(current), emptyList(), null, PillRegimenConfig())!!

        assertTrue(countdown.isLate)
        assertEquals(41, countdown.dayIndex)
    }
}
