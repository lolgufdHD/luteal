package fr.luteal.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.CyclePeriodRing
import fr.luteal.core.designsystem.theme.LocalPhaseColors
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.CountdownBasis
import fr.luteal.core.model.CountdownPhase
import fr.luteal.core.model.CycleCountdown
import fr.luteal.core.model.CycleRingData
import java.time.LocalDate

/**
 * The countdown ring with its centre text and legend. Used on the tracker's
 * Today screen and, when shared, on the Duo partner's screen.
 */
@Composable
internal fun CountdownRingView(
    ring: CycleRingData,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val countdown = ring.countdown
    val day = countdown.dayIndex
    val todayHasFlow = ring.flowByDay[day]?.isPeriodFlow() == true
    val isPill = countdown.basis == CountdownBasis.PILL_PACK
    val nextDate = LocalizedDateFormatter.formatShortDate(countdown.nextStart, locale)

    val todayWord = stringResource(R.string.today_title)
    val todayLine = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(todayWord) }
        append(", ")
        append(LocalizedDateFormatter.formatShortDate(today, locale))
    }
    val daysLeft = countdown.daysUntilNext
    val daysLate = day - countdown.length
    val headline = when {
        todayHasFlow -> stringResource(R.string.cycle_ring_period_day, day)
        countdown.isLate -> pluralStringResource(R.plurals.countdown_days_late, daysLate, daysLate)
        daysLeft <= 1 -> stringResource(if (isPill) R.string.countdown_pause_tomorrow else R.string.countdown_period_tomorrow)
        isPill -> pluralStringResource(R.plurals.countdown_days_until_pause, daysLeft, daysLeft)
        else -> pluralStringResource(R.plurals.countdown_days_until_period, daysLeft, daysLeft)
    }
    val support = when (countdown.basis) {
        CountdownBasis.PILL_PACK -> stringResource(R.string.countdown_support_pill, nextDate)
        CountdownBasis.PERSONAL -> ring.estimateWindow?.let {
            stringResource(
                R.string.estimate_window_subhead,
                LocalizedDateFormatter.formatShortDate(it.start, locale),
                LocalizedDateFormatter.formatShortDate(it.endInclusive, locale)
            )
        }
        CountdownBasis.TYPICAL -> stringResource(R.string.countdown_support_typical, nextDate)
    }
    val probablePhase = countdown.phaseOn(day)?.takeUnless { todayHasFlow || countdown.isLate }
    val phaseLine = probablePhase?.let { stringResource(R.string.countdown_probably, countdownPhaseLabel(it)) }
    val a11y = listOfNotNull(
        stringResource(R.string.cycle_day, day),
        headline,
        support,
        phaseLine
    ).joinToString(". ")

    val phaseTints = buildMap {
        countdown.segments.forEach { segment ->
            val color = countdownPhaseColor(segment.phase)
            for (d in segment.firstDay..segment.lastDay) put(d, color)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        CyclePeriodRing(
            ringLength = countdown.length,
            dayOfCycle = day,
            flowByDay = ring.flowByDay,
            estimatedDays = ring.estimatedDays,
            dayLabel = stringResource(R.string.cycle_ring_day_label),
            phaseTints = phaseTints,
            accessibleLabel = a11y,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Text(
                text = todayLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            support?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            phaseLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
        CountdownLegend(countdown)
    }
}

@Composable
internal fun countdownPhaseColor(phase: CountdownPhase): Color {
    val phases = LocalPhaseColors.current
    return when (phase) {
        CountdownPhase.BLEED -> phases.menstrual.content.copy(alpha = 0.45f)
        CountdownPhase.FOLLICULAR -> phases.follicular.content.copy(alpha = 0.45f)
        CountdownPhase.OVULATION -> phases.ovulatory.content.copy(alpha = 0.6f)
        CountdownPhase.LUTEAL -> phases.luteal.content.copy(alpha = 0.45f)
        CountdownPhase.PILL_ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }
}

@Composable
internal fun countdownPhaseLabel(phase: CountdownPhase): String = stringResource(
    when (phase) {
        CountdownPhase.BLEED -> R.string.countdown_phase_bleed
        CountdownPhase.FOLLICULAR -> R.string.countdown_phase_follicular
        CountdownPhase.OVULATION -> R.string.countdown_phase_ovulation
        CountdownPhase.LUTEAL -> R.string.countdown_phase_luteal
        CountdownPhase.PILL_ACTIVE -> R.string.countdown_phase_pill_active
    }
)

/** What each tint on the ring means; every phase except the bleed is an estimate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountdownLegend(countdown: CycleCountdown) {
    val phases = countdown.segments.map { it.phase }.distinct()
    FlowRow(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
    ) {
        phases.forEach { phase ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(countdownPhaseColor(phase), CircleShape)
                )
                Text(
                    text = countdownPhaseLabel(phase),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
