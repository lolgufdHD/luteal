package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.theme.FlowColors
import fr.luteal.core.designsystem.theme.FlowTone
import fr.luteal.core.designsystem.theme.LocalFlowColors
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.designsystem.theme.tabular
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CalendarDayProjection
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.MonthCalendarProjection
import fr.luteal.core.model.PhaseCertainty
import fr.luteal.core.model.PillDayStatus
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Horizontal and vertical gap between day tiles. */
val CalendarTileSpacing: Dp = 4.dp

private val CalendarTileHeight = 58.dp
private val CalendarTileShape = RoundedCornerShape(4.dp)

/** Fill and legible text colour for a recorded flow intensity, or null when there is no period flow. */
fun FlowColors.toneFor(intensity: BleedingIntensity?): FlowTone? = when (intensity) {
    BleedingIntensity.LIGHT -> light
    BleedingIntensity.MEDIUM -> medium
    BleedingIntensity.HEAVY -> heavy
    else -> null
}

/**
 * One month as a grid of tiles. Days outside the month are left blank so a
 * vertically scrolling stack of months never shows the same date twice.
 *
 * Recorded period days carry a bar behind the day number whose colour
 * deepens with the recorded flow; a run of consecutive days reads as one bar
 * with rounded ends. Estimated period days get a dashed outline only, so an
 * estimate can never be mistaken for a record.
 */
@Composable
fun MonthCalendarGrid(
    projection: MonthCalendarProjection,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    showWeekdayHeader: Boolean = true
) {
    val days = remember(projection) { projection.weeks.flatten() }
    val flowDates = remember(days) { days.filter { it.hasPeriodFlow }.map { it.date }.toSet() }
    val estimatedDates = remember(days) {
        days.filter { it.isEstimatedPeriodWindow || it.isEstimatedPeriodTarget }.map { it.date }.toSet()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CalendarTileSpacing)
    ) {
        if (showWeekdayHeader) {
            CalendarWeekdayHeader()
        }

        projection.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalendarTileSpacing)
            ) {
                week.forEach { day ->
                    if (!day.isCurrentMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val runDates = when {
                            day.hasPeriodFlow -> flowDates
                            day.date in estimatedDates -> estimatedDates
                            else -> emptySet()
                        }
                        CalendarDayCell(
                            day = day,
                            isSelected = day.date == selectedDate,
                            onClick = { onSelectDate(day.date) },
                            joinsPrevious = day.date.minusDays(1) in runDates,
                            joinsNext = day.date.plusDays(1) in runDates,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarWeekdayHeader(modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val daysOfWeek = (0L..6L).map { firstDayOfWeek.plus(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LutealSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CalendarTileSpacing)
    ) {
        daysOfWeek.forEach { day ->
            val label = day.getDisplayName(TextStyle.NARROW, locale)
            Text(
                text = label.uppercase(locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                    }
            )
        }
    }
}

@Composable
fun CalendarDayCell(
    day: CalendarDayProjection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    joinsPrevious: Boolean = false,
    joinsNext: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val flowColors = LocalFlowColors.current
    val flowTone = if (day.hasPeriodFlow) flowColors.toneFor(day.bleedingIntensity) else null
    val isEstimated = flowTone == null && (day.isEstimatedPeriodWindow || day.isEstimatedPeriodTarget)

    val cellBorder: BorderStroke? = when {
        day.isToday -> BorderStroke(2.dp, scheme.primary)
        isSelected -> BorderStroke(2.dp, scheme.onSurfaceVariant)
        else -> null
    }

    val textColor: Color = when {
        flowTone != null -> flowTone.onFill
        day.isToday -> scheme.primary
        day.isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> scheme.onSurface
    }

    val cdString = calendarDayDescription(day)

    // Rounded only where a run of period days starts or ends, so consecutive
    // days visually join into one bar across the tile gaps.
    val barShape = RoundedCornerShape(
        topStartPercent = if (joinsPrevious) 0 else 50,
        bottomStartPercent = if (joinsPrevious) 0 else 50,
        topEndPercent = if (joinsNext) 0 else 50,
        bottomEndPercent = if (joinsNext) 0 else 50
    )

    Box(
        modifier = modifier
            .height(CalendarTileHeight)
            .clip(CalendarTileShape)
            .background(scheme.surfaceVariant)
            .then(
                if (cellBorder != null) Modifier.border(cellBorder, CalendarTileShape)
                else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = cdString }
    ) {
        // Indicators, top-left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 5.dp, top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (day.isSpottingOnly) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 10.dp)
                        .background(flowColors.spotting, RoundedCornerShape(1.5.dp))
                )
            }
            when (val pill = day.pillStatus) {
                is PillDayStatus.ActiveDay -> CalendarPillCapsule(
                    color = scheme.primary,
                    filled = pill.isTakenToday
                )
                is PillDayStatus.PauseDay -> CalendarPillCapsule(
                    color = scheme.outline,
                    filled = false
                )
                PillDayStatus.Disabled -> Unit
            }
            if (day.hasObservations) {
                CalendarDot(color = scheme.secondary, size = 6.dp)
            }
            if (day.hasBiomarkers) {
                CalendarDot(color = scheme.tertiary, size = 6.dp)
            }
        }

        // Period bar with the day number, bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .fillMaxWidth()
                .height(26.dp)
                .then(
                    when {
                        flowTone != null -> Modifier.background(flowTone.fill, barShape)
                        isEstimated -> Modifier.dashedOutline(
                            shape = barShape,
                            color = flowColors.estimated,
                            fill = if (day.isEstimatedPeriodTarget) {
                                flowColors.estimated.copy(alpha = 0.22f)
                            } else {
                                Color.Transparent
                            },
                            drawStart = !joinsPrevious,
                            drawEnd = !joinsNext
                        )
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.tabular().copy(
                    fontWeight = if (day.isToday || flowTone != null) FontWeight.Bold else FontWeight.Normal
                ),
                color = textColor,
                modifier = Modifier.padding(end = 7.dp)
            )
        }
    }
}

/**
 * Dashed outline for estimated days. Where a run continues into the next
 * tile the open side is left undrawn, so the dashes read as one band.
 */
private fun Modifier.dashedOutline(
    shape: Shape,
    color: Color,
    fill: Color,
    drawStart: Boolean,
    drawEnd: Boolean
): Modifier = drawBehind {
    val strokeWidth = 1.5.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    if (fill != Color.Transparent) {
        drawOutline(outline = outline, color = fill)
    }
    val inset = strokeWidth / 2f
    val clipLeft = if (drawStart) 0f else inset + 0.5f
    val clipRight = if (drawEnd) size.width else size.width - inset - 0.5f
    clipRect(left = clipLeft, right = clipRight) {
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            )
        )
    }
}

@Composable
private fun calendarDayDescription(day: CalendarDayProjection): String {
    val locale = LocalConfiguration.current.locales[0]
    val formattedDate = LocalizedDateFormatter.formatFullDate(day.date, locale)
    return buildString {
        append(formattedDate)
        if (day.isToday) {
            append(", ")
            append(stringResource(R.string.calendar_today_button))
        }
        if (day.cycleDayNumber != null) {
            append(", ")
            append(stringResource(R.string.inspection_cycle_day, day.cycleDayNumber))
        }
        when {
            day.hasPeriodFlow -> {
                append(", ")
                val intensityLabel = when (day.bleedingIntensity) {
                    BleedingIntensity.LIGHT -> stringResource(R.string.bleeding_light)
                    BleedingIntensity.MEDIUM -> stringResource(R.string.bleeding_medium)
                    BleedingIntensity.HEAVY -> stringResource(R.string.bleeding_heavy)
                    else -> null
                }
                if (intensityLabel != null) {
                    append("$intensityLabel, ")
                }
                append(stringResource(R.string.calendar_legend_recorded_period))
            }
            day.isSpottingOnly -> {
                append(", ")
                append(stringResource(R.string.bleeding_spotting))
            }
            day.isEstimatedPeriodTarget -> {
                append(", ")
                append(stringResource(R.string.calendar_legend_estimated_period_target))
            }
            day.isEstimatedPeriodWindow -> {
                append(", ")
                append(stringResource(R.string.calendar_legend_estimated_period))
            }
            day.cyclePhase != null -> {
                append(", ")
                val phaseLabel = when (day.cyclePhase) {
                    CyclePhase.MENSTRUAL -> stringResource(R.string.phase_menstrual)
                    CyclePhase.FOLLICULAR -> stringResource(R.string.phase_follicular)
                    CyclePhase.OVULATORY -> stringResource(R.string.phase_ovulatory)
                    CyclePhase.LUTEAL -> stringResource(R.string.phase_luteal)
                }
                if (day.phaseCertainty == PhaseCertainty.RECORDED) {
                    append(stringResource(R.string.inspection_phase_recorded, phaseLabel))
                } else {
                    append(stringResource(R.string.inspection_phase_estimated, phaseLabel))
                }
            }
        }
        if (day.hasObservations) {
            append(", ")
            append(stringResource(R.string.calendar_legend_observation))
        }
        if (day.hasBiomarkers) {
            append(", ")
            append(stringResource(R.string.calendar_legend_biomarkers))
        }
        when (val pill = day.pillStatus) {
            is PillDayStatus.ActiveDay -> {
                append(", ")
                if (pill.isTakenToday) {
                    append(stringResource(R.string.calendar_legend_pill_taken))
                } else {
                    append(stringResource(R.string.calendar_legend_pill_scheduled))
                }
            }
            is PillDayStatus.PauseDay -> {
                append(", ")
                append(stringResource(R.string.calendar_legend_pill_pause))
            }
            PillDayStatus.Disabled -> Unit
        }
    }
}

@Composable
private fun CalendarDot(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Diagonal two-tone capsule (classic pill glyph).
 * - filled = true: one half solid, the other half tinted (pill taken)
 * - filled = false: outline with a centre seam only (scheduled / pause)
 */
@Composable
fun CalendarPillCapsule(color: Color, filled: Boolean, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        val length = this.size.minDimension - strokeWidth
        val thickness = length * 0.42f
        val topLeft = Offset(
            x = (this.size.width - length) / 2f,
            y = (this.size.height - thickness) / 2f
        )
        val capsuleSize = Size(length, thickness)
        val radius = CornerRadius(thickness / 2f)

        rotate(degrees = -45f) {
            if (filled) {
                drawRoundRect(
                    color = color.copy(alpha = 0.35f),
                    topLeft = topLeft,
                    size = capsuleSize,
                    cornerRadius = radius
                )
                clipRect(right = center.x) {
                    drawRoundRect(
                        color = color,
                        topLeft = topLeft,
                        size = capsuleSize,
                        cornerRadius = radius
                    )
                }
            }
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = capsuleSize,
                cornerRadius = radius,
                style = Stroke(width = strokeWidth)
            )
            drawLine(
                color = color,
                start = Offset(center.x, topLeft.y),
                end = Offset(center.x, topLeft.y + thickness),
                strokeWidth = strokeWidth
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarLegendCard(
    showPillLegend: Boolean = false,
    modifier: Modifier = Modifier
) {
    val flowColors = LocalFlowColors.current
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = LutealSpacing.sm, vertical = LutealSpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.calendar_legend_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.calendar_legend_toggle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = LutealSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
                ) {
                    LegendItem(label = stringResource(R.string.bleeding_light)) {
                        LegendBar(color = flowColors.light.fill)
                    }
                    LegendItem(label = stringResource(R.string.bleeding_medium)) {
                        LegendBar(color = flowColors.medium.fill)
                    }
                    LegendItem(label = stringResource(R.string.bleeding_heavy)) {
                        LegendBar(color = flowColors.heavy.fill)
                    }
                    LegendItem(label = stringResource(R.string.calendar_legend_spotting)) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 10.dp)
                                .background(flowColors.spotting, RoundedCornerShape(1.5.dp))
                        )
                    }
                    LegendItem(label = stringResource(R.string.calendar_legend_estimated_period)) {
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 10.dp)
                                .dashedOutline(
                                    shape = RoundedCornerShape(50),
                                    color = flowColors.estimated,
                                    fill = Color.Transparent,
                                    drawStart = true,
                                    drawEnd = true
                                )
                        )
                    }
                    LegendItem(label = stringResource(R.string.calendar_legend_observation)) {
                        CalendarDot(color = MaterialTheme.colorScheme.secondary, size = 6.dp)
                    }
                    LegendItem(label = stringResource(R.string.calendar_legend_biomarkers)) {
                        CalendarDot(color = MaterialTheme.colorScheme.tertiary, size = 6.dp)
                    }
                    if (showPillLegend) {
                        LegendItem(label = stringResource(R.string.calendar_legend_pill_taken)) {
                            CalendarPillCapsule(color = MaterialTheme.colorScheme.primary, filled = true)
                        }
                        LegendItem(label = stringResource(R.string.calendar_legend_pill_scheduled)) {
                            CalendarPillCapsule(color = MaterialTheme.colorScheme.primary, filled = false)
                        }
                        LegendItem(label = stringResource(R.string.calendar_legend_pill_pause)) {
                            CalendarPillCapsule(color = MaterialTheme.colorScheme.outline, filled = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
    ) {
        swatch()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendBar(color: Color) {
    Box(
        modifier = Modifier
            .width(22.dp)
            .height(10.dp)
            .background(color, RoundedCornerShape(50))
    )
}
