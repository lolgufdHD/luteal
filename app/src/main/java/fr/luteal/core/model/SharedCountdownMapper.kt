package fr.luteal.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Converts the tracker's [CycleRingData] to the [SharedCountdown] sealed into
 * the Duo projection, and back on the partner's device.
 */
object SharedCountdownMapper {

    private const val MAX_RING_DAYS = 120

    fun compose(today: LocalDate, ring: CycleRingData): SharedCountdown {
        val countdown = ring.countdown
        return SharedCountdown(
            date = today.toString(),
            anchor = countdown.anchor.toString(),
            length = countdown.length,
            basis = countdown.basis.name.lowercase(),
            segments = countdown.segments.map {
                SharedCountdownSegment(it.phase.name.lowercase(), it.firstDay, it.lastDay)
            },
            flow = ring.flowByDay.toSortedMap().map { (day, intensity) ->
                SharedRingFlow(day, intensity.name.lowercase())
            },
            estimatedDays = ring.estimatedDays.sorted(),
            windowStart = ring.estimateWindow?.start?.toString(),
            windowEnd = ring.estimateWindow?.endInclusive?.toString()
        )
    }

    /**
     * Rebuilds the ring for the partner's [today], so the day count keeps
     * moving between payloads. A pill pack that has ended in the meantime is
     * rolled forward to the current pack. Null when the payload is malformed.
     */
    fun toRingData(shared: SharedCountdown, today: LocalDate): CycleRingData? = runCatching {
        val basis = CountdownBasis.entries.first { it.name.equals(shared.basis, ignoreCase = true) }
        val length = shared.length
        require(length in 1..MAX_RING_DAYS)
        var anchor = LocalDate.parse(shared.anchor)
        val segments = shared.segments.mapNotNull { segment ->
            CountdownPhase.entries.firstOrNull { it.name.equals(segment.phase, ignoreCase = true) }
                ?.let { CountdownSegment(it, segment.firstDay, segment.lastDay) }
        }
        var flow = shared.flow.mapNotNull { day ->
            BleedingIntensity.entries.firstOrNull { it.name.equals(day.intensity, ignoreCase = true) }
                ?.let { day.day to it }
        }.toMap()
        var estimated = shared.estimatedDays.toSet()

        if (basis == CountdownBasis.PILL_PACK) {
            val packsPassed = Math.floorDiv(ChronoUnit.DAYS.between(anchor, today), length.toLong())
            if (packsPassed > 0) {
                anchor = anchor.plusDays(packsPassed * length)
                flow = emptyMap()
                estimated = emptySet()
            }
        }
        val day = ChronoUnit.DAYS.between(anchor, today).toInt() + 1
        require(day >= 1)

        val window = if (shared.windowStart != null && shared.windowEnd != null) {
            LocalDate.parse(shared.windowStart)..LocalDate.parse(shared.windowEnd)
        } else {
            null
        }
        CycleRingData(
            countdown = CycleCountdown(
                anchor = anchor,
                length = length,
                dayIndex = day,
                nextStart = anchor.plusDays(length.toLong()),
                basis = basis,
                segments = segments
            ),
            flowByDay = flow.filterKeys { it in 1..day },
            estimatedDays = estimated.filter { it > day }.toSet(),
            estimateWindow = window
        )
    }.getOrNull()
}
