package fr.luteal.core.model

import fr.luteal.core.network.ContractJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SharedCountdownMapperTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun naturalRing(): CycleRingData {
        val current = Cycle(id = "c", startDate = LocalDate.of(2026, 11, 12))
        val entries = listOf(
            DailyEntry(date = LocalDate.of(2026, 11, 12), bleedingIntensity = BleedingIntensity.HEAVY),
            DailyEntry(date = LocalDate.of(2026, 11, 13), bleedingIntensity = BleedingIntensity.MEDIUM)
        )
        val estimate = CycleEstimate(
            earliestDate = LocalDate.of(2026, 12, 7),
            centralDate = LocalDate.of(2026, 12, 10),
            latestDate = LocalDate.of(2026, 12, 14),
            cycleCount = 2,
            variabilityDays = 2
        )
        val countdown = CycleCountdownCalculator.evaluate(today, current, listOf(current), entries, estimate, PillRegimenConfig())!!
        return CycleRingDataBuilder.build(today, countdown, listOf(current), entries, estimate)
    }

    @Test
    fun `round trip on the same day reproduces the ring`() {
        val ring = naturalRing()
        val json = ContractJson.encodeToString(SharedCountdown.serializer(), SharedCountdownMapper.compose(today, ring))
        val back = SharedCountdownMapper.toRingData(ContractJson.decodeFromString(SharedCountdown.serializer(), json), today)!!

        assertEquals(ring.countdown, back.countdown)
        assertEquals(ring.flowByDay, back.flowByDay)
        assertEquals(ring.estimatedDays, back.estimatedDays)
        assertEquals(ring.estimateWindow, back.estimateWindow)
    }

    @Test
    fun `partner a few days later sees the day count move on`() {
        val shared = SharedCountdownMapper.compose(today, naturalRing())
        val back = SharedCountdownMapper.toRingData(shared, today.plusDays(3))!!

        assertEquals(17, back.countdown.dayIndex)
        assertTrue(back.estimatedDays.all { it > 17 })
    }

    @Test
    fun `an old pill pack rolls forward to the current pack`() {
        val pill = PillRegimenConfig(isEnabled = true, activeDays = 21, pauseDays = 7, startDate = LocalDate.of(2026, 10, 21))
        val countdown = CycleCountdownCalculator.evaluate(today, null, emptyList(), emptyList(), null, pill)!!
        val shared = SharedCountdownMapper.compose(today, CycleRingDataBuilder.build(today, countdown, emptyList(), emptyList(), null))

        val back = SharedCountdownMapper.toRingData(shared, LocalDate.of(2026, 12, 10))!!

        assertEquals(LocalDate.of(2026, 12, 9), back.countdown.anchor)
        assertEquals(2, back.countdown.dayIndex)
        assertEquals(CountdownPhase.BLEED, back.countdown.phaseOn(2))
    }

    @Test
    fun `malformed payload yields no ring`() {
        val shared = SharedCountdown(
            date = "2026-11-25",
            anchor = "not-a-date",
            length = 28,
            basis = "typical",
            segments = emptyList()
        )
        assertNull(SharedCountdownMapper.toRingData(shared, today))
    }
}
