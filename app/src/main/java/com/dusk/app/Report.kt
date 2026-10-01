package com.dusk.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** A colored, slightly tilted card with a little "magnet" dot on top. */
@Composable
fun Magnet(
    t: Tone,
    tilt: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val d = tone(t)
    Surface(
        color = d.bg,
        contentColor = d.fg,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 3.dp,
        modifier = modifier.graphicsLayer { rotationZ = tilt }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(10.dp).clip(CircleShape)
                    .background(d.fg.copy(alpha = 0.3f))
            )
            content()
        }
    }
}

@Composable
fun MagnetTitle(icon: Int, text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TIcon(icon, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = style)
    }
}

/** Two big tiles: the quit day and the first milestone. */
@Composable
fun QuitTiles(lines: List<Pair<String, LocalDate>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Magnet(Tone.Coral, -2f, Modifier.weight(1f)) {
            MagnetTitle(R.drawable.ic_t_calendar_event, "Quit day", MaterialTheme.typography.labelMedium)
            lines.forEach { (label, date) ->
                if (lines.size > 1) Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE d")),
                    fontFamily = Fraunces, fontSize = if (lines.size > 1) 24.sp else 32.sp, lineHeight = 34.sp
                )
            }
            Text(lines.first().second.format(DateTimeFormatter.ofPattern("MMMM")), style = MaterialTheme.typography.labelMedium)
        }
        Magnet(Tone.Sea, 1.5f, Modifier.weight(1f)) {
            MagnetTitle(R.drawable.ic_t_flag, "First milestone", MaterialTheme.typography.labelMedium)
            Text("Day 3", fontFamily = Fraunces, fontSize = 32.sp, lineHeight = 34.sp)
            Text(lines.first().second.plusDays(2).format(shortDate), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// Withdrawal intensity (0..1) by day, shaped from the timelines the coach uses.
private val CIG_CURVE = listOf(0f to 0.2f, 1f to 0.75f, 2.5f to 1f, 4f to 0.8f, 7f to 0.55f, 14f to 0.3f, 28f to 0.12f)
private val CAN_CURVE = listOf(0f to 0.1f, 1f to 0.4f, 3f to 0.9f, 5f to 1f, 7f to 0.7f, 14f to 0.35f, 28f to 0.15f)

/** Early days get more room: the first week is where everything happens. */
private fun xOf(day: Float): Float = sqrt(day / 28f)

private fun valueAt(curve: List<Pair<Float, Float>>, day: Float): Float {
    for (i in 0 until curve.size - 1) {
        val (d0, v0) = curve[i]
        val (d1, v1) = curve[i + 1]
        if (day in d0..d1) {
            val t = (day - d0) / (d1 - d0)
            val eased = ((1 - cos(PI * t)) / 2).toFloat()
            return v0 + (v1 - v0) * eased
        }
    }
    return curve.last().second
}

@Composable
fun TideChart(subs: List<String>, day1: LocalDate) {
    val c = MaterialTheme.colorScheme
    Magnet(Tone.Sand, -0.8f, Modifier.fillMaxWidth()) {
        MagnetTitle(R.drawable.ic_t_wave_sine, "Withdrawal tide")
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val w = size.width
            val h = size.height
            subs.forEach { s ->
                val curve = if (s == FLOW_CIGARETTE) CIG_CURVE else CAN_CURVE
                val color = if (s == FLOW_CIGARETTE) c.secondary else c.primary
                val path = Path()
                val steps = 60
                for (i in 0..steps) {
                    val day = 28f * (i.toFloat() / steps).let { it * it }
                    val x = w * xOf(day)
                    val y = h - 6.dp.toPx() - valueAt(curve, day) * (h - 14.dp.toPx())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawLine(
                c.outline, Offset(0f, h - 2.dp.toPx()), Offset(w, h - 2.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            listOf(1, 3, 7, 14, 28).forEach { dd ->
                val x = maxWidth * xOf(dd.toFloat())
                Text(
                    if (dd == 1) "Day 1" else "$dd",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(x = (x - 8.dp).coerceIn(0.dp, maxWidth - 32.dp))
                )
            }
        }
        subs.forEach { s ->
            val (from, to) = if (s == FLOW_CIGARETTE) 2L to 3L else 2L to 6L
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subs.size > 1) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (s == FLOW_CIGARETTE) c.secondary else c.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "${if (subs.size > 1) flowName(s) + " peak" else "Peak"}: " +
                        "${day1.plusDays(from - 1).format(shortDate)} to ${day1.plusDays(to - 1).format(shortDate)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Trigger → swap table, from the routine items the coach tagged. */
@Composable
fun SwapMagnet(tasks: List<Task>) {
    val swaps = tasks.filter { it.replaces.isNotBlank() }
    if (swaps.isEmpty()) return
    Magnet(Tone.Mint, 0.8f, Modifier.fillMaxWidth()) {
        MagnetTitle(R.drawable.ic_t_arrows_exchange, "Your triggers, swapped")
        swaps.forEach { t ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    t.replaces.replaceFirstChar { it.uppercase() },
                    Modifier.weight(0.9f),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium
                )
                Text("→", Modifier.width(22.dp), style = MaterialTheme.typography.bodySmall)
                Text(t.title, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun minutes(time: String): Int = time.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

/** The day as a line with a colored dot for every routine item. */
@Composable
fun DayStrip(tasks: List<Task>) {
    if (tasks.isEmpty()) return
    val first = minutes(tasks.first().time)
    val last = minutes(tasks.last().time).coerceAtLeast(first + 1)
    Magnet(Tone.Sand, -1f, Modifier.fillMaxWidth()) {
        MagnetTitle(R.drawable.ic_t_clock, "Your day")
        BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            tasks.forEach { t ->
                val f = (minutes(t.time) - first).toFloat() / (last - first)
                val k = kindDuo(t.kind)
                Box(
                    Modifier.offset(x = (maxWidth - 18.dp) * f, y = 4.dp).size(18.dp).clip(CircleShape)
                        .background(k.fg)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(tasks.first().time, style = MaterialTheme.typography.labelSmall)
            Text(tasks.last().time, style = MaterialTheme.typography.labelSmall)
        }
        val kinds = tasks.map { it.kind }.filter { it.isNotBlank() }.distinct()
        if (kinds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { kinds.forEach { KindChip(it) } }
        }
    }
}

/** A gold note for things worth knowing before day 1. */
@Composable
fun HeadsUp(text: String, tilt: Float) {
    Magnet(Tone.Gold, tilt, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            TIcon(R.drawable.ic_t_bulb, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The full routine, as a magnet. */
@Composable
fun RoutineMagnet(tasks: List<Task>) {
    Magnet(Tone.Sand, 0.6f, Modifier.fillMaxWidth()) {
        MagnetTitle(R.drawable.ic_t_list_check, "Daily routine")
        tasks.forEach { t ->
            val k = kindDuo(t.kind)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                TIcon(kindIcon(t.kind), size = 16.dp, tint = k.fg)
                Spacer(Modifier.width(10.dp))
                Text(t.time, Modifier.width(50.dp), style = MaterialTheme.typography.labelLarge)
                Text(t.title, style = MaterialTheme.typography.bodyMedium, color = LocalTextColor())
            }
        }
    }
}

@Composable
private fun LocalTextColor(): Color = androidx.compose.material3.LocalContentColor.current
