package fr.luteal.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.luteal.core.designsystem.theme.LocalFlowColors
import fr.luteal.core.designsystem.theme.tabular
import fr.luteal.core.model.BleedingIntensity
import kotlin.math.cos
import kotlin.math.sin

/**
 * The current cycle drawn as a ring, one slice per day, starting at the top
 * and running clockwise.
 *
 * - Recorded flow days are thick arcs coloured by intensity (deeper red =
 *   heavier), merged into one rounded band per run.
 * - The inner dot row has one dot per day; days inside the *estimated* next
 *   period window are tinted, never drawn as a solid band, so an estimate
 *   cannot be read as a record.
 * - The gap at the top, marked with a drop, is where the next period is
 *   expected to begin.
 *
 * The ring needs a total to divide by. Callers must only pass a
 * [ringLength] derived from the user's own estimate (never a fixed 28-day
 * assumption), and fall back to [CycleDayRing] when no estimate exists.
 */
@Composable
fun CyclePeriodRing(
    ringLength: Int,
    dayOfCycle: Int,
    flowByDay: Map<Int, BleedingIntensity>,
    estimatedDays: Set<Int>,
    dayLabel: String,
    accessibleLabel: String,
    modifier: Modifier = Modifier,
    centerContent: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val flowColors = LocalFlowColors.current
    val length = ringLength.coerceAtLeast(dayOfCycle).coerceAtLeast(1)
    val gapDegrees = 30f
    val sweepTotal = 360f - gapDegrees
    val perDay = sweepTotal / length
    val startAngle = -90f + gapDegrees / 2f
    fun angleAt(dayIndex: Float): Float = startAngle + dayIndex * perDay

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = accessibleLabel },
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val stroke = diameter * 0.085f
        val markerSize = stroke * 2.1f
        val radius = diameter / 2 - markerSize / 2 - 2.dp
        val trackColor = scheme.surfaceVariant
        val dotColor = scheme.outline
        val todayAngle = Math.toRadians(angleAt(dayOfCycle - 0.5f).toDouble())

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val radiusPx = radius.toPx()
            val arcTopLeft = Offset(center.x - radiusPx, center.y - radiusPx)
            val arcSize = Size(radiusPx * 2f, radiusPx * 2f)

            fun pointAt(angleDeg: Float, r: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(
                    x = center.x + r * cos(rad).toFloat(),
                    y = center.y + r * sin(rad).toFloat()
                )
            }

            // Base track for the whole cycle
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Recorded flow, one slice per day, rounded at each run's ends
            for (day in 1..length) {
                val tone = flowColors.toneFor(flowByDay[day]) ?: continue
                drawArc(
                    color = tone.fill,
                    startAngle = angleAt(day - 1f),
                    // A hair of overlap avoids anti-aliasing seams between slices.
                    sweepAngle = perDay + if (day < length) 0.4f else 0f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )
                val startsRun = flowColors.toneFor(flowByDay[day - 1]) == null
                val endsRun = flowColors.toneFor(flowByDay[day + 1]) == null
                if (startsRun) {
                    drawCircle(tone.fill, strokePx / 2f, pointAt(angleAt(day - 1f), radiusPx))
                }
                if (endsRun) {
                    drawCircle(tone.fill, strokePx / 2f, pointAt(angleAt(day.toFloat()), radiusPx))
                }
            }

            // Inner dot row, one dot per day
            if (length <= 90) {
                val dotRadius = radiusPx - strokePx / 2f - 12.dp.toPx()
                for (day in 1..length) {
                    if (day == dayOfCycle) continue
                    val isEstimated = day in estimatedDays
                    val isSpotting = flowByDay[day] == BleedingIntensity.SPOTTING
                    drawCircle(
                        color = when {
                            isEstimated -> flowColors.estimated
                            isSpotting -> flowColors.spotting
                            else -> dotColor.copy(alpha = 0.55f)
                        },
                        radius = if (isEstimated || isSpotting) 2.6.dp.toPx() else 1.8.dp.toPx(),
                        center = pointAt(angleAt(day - 0.5f), dotRadius)
                    )
                }
            }
        }

        // Drop in the gap: where the next period is expected to begin
        Icon(
            imageVector = Icons.Rounded.WaterDrop,
            contentDescription = null,
            tint = flowColors.medium.fill,
            modifier = Modifier
                .offset(y = -radius)
                .size(stroke * 1.1f)
        )

        // Centre text
        Column(
            modifier = Modifier.width((radius - stroke) * 1.55f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = centerContent
        )

        // Today marker on the ring
        Surface(
            modifier = Modifier
                .offset(
                    x = radius * cos(todayAngle).toFloat(),
                    y = radius * sin(todayAngle).toFloat()
                )
                .size(markerSize),
            shape = CircleShape,
            color = scheme.surface,
            border = BorderStroke(2.dp, scheme.outline)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = dayOfCycle.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.Bold),
                    color = scheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
