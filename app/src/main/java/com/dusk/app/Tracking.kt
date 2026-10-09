package com.dusk.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Numbers behind the Progress tab. Everything comes from what the person logged. */
object Track {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private const val OPEN_EXPIRY_MS = 3 * 60 * 60_000L

    /** Cravings with a known ending. Open ones older than 3 hours don't count either way. */
    fun resolved(): List<CravingEvent> = Store.cravings.filter { it.outcome == "passed" || it.outcome == "gave_in" }
    fun passed(): Int = Store.cravings.count { it.outcome == "passed" }
    fun gaveIn(): Int = Store.cravings.count { it.outcome == "gave_in" }

    fun passRate(): Int? = resolved().size.takeIf { it > 0 }?.let { passed() * 100 / it }

    /** Typical minutes from "craving" to "it passed" (median, so one long one doesn't skew it). */
    fun minutesToPass(): Int? {
        val d = Store.cravings.filter { it.outcome == "passed" && it.end > it.start }
            .map { ((it.end - it.start) / 60_000L).toInt().coerceAtLeast(1) }.sorted()
        return if (d.isEmpty()) null else d[d.size / 2]
    }

    fun openNow(now: Long): Boolean = Store.cravings.any { it.outcome == "open" && now - it.start < OPEN_EXPIRY_MS }

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun hourOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zone).hour

    /** Last 7 days: (day letter, passed, gave in). */
    fun week(): List<Triple<String, Int, Int>> {
        val today = LocalDate.now()
        return (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val day = Store.cravings.filter { dateOf(it.start) == d }
            Triple(
                d.format(DateTimeFormatter.ofPattern("EEEEE")),
                day.count { it.outcome == "passed" },
                day.count { it.outcome == "gave_in" }
            )
        }
    }

    /** Cravings by hour of day, all logged cravings. */
    fun byHour(): IntArray {
        val a = IntArray(24)
        Store.cravings.forEach { a[hourOf(it.start)] += 1 }
        return a
    }

    /** The hour cravings hit most, once there are at least 2 there. */
    fun peakHour(): Int? {
        val a = byHour()
        val max = a.maxOrNull() ?: 0
        return if (max >= 2) a.indexOfFirst { it == max } else null
    }

    fun cravingsToday(): Int = Store.cravings.count { dateOf(it.start) == LocalDate.now() }

    private fun mins(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    /** Likely cravings for the rest of today: routine triggers, plus the hour the log says they hit. */
    fun comingUp(): List<String> {
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val items = Store.tasks.filter { it.replaces.isNotBlank() && mins(it.time) > nowMin }
            .map { mins(it.time) to "${it.replaces.replaceFirstChar { c -> c.uppercase() }} around ${it.time}" }
            .toMutableList()
        peakHour()?.let { h ->
            if (h * 60 > nowMin && items.none { kotlin.math.abs(it.first - h * 60) < 60 }) {
                items.add(h * 60 to "Your log says they often hit around ${"%02d".format(h)}:00")
            }
        }
        return items.sortedBy { it.first }.take(3).map { it.second }
    }

    /** Typical withdrawal level today for each substance that's started. */
    fun withdrawalToday(now: Long): List<Pair<String, String>> = Store.tideSubstances().mapNotNull { s ->
        val st = Store.startMs(s)
        if (st <= 0 || st > now) return@mapNotNull null
        val days = (now - st) / 86_400_000f
        val v = withdrawalLevel(s, days)
        val label = when {
            days > 28 -> "Low. The long night: rare, but sneaky."
            v >= 0.75f -> "High. Typical peak window."
            v >= 0.4f -> "Medium. Easing, still loud at times."
            else -> "Low. Quieter, which is its own risk."
        }
        s to label
    }

    /** "3d 14h 22m" since a moment. */
    fun since(ms: Long, now: Long): String {
        val m = ((now - ms) / 60_000L).coerceAtLeast(0)
        val d = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        return when {
            d > 0 -> "${d}d ${h}h ${mm}m"
            h > 0 -> "${h}h ${mm}m"
            else -> "${mm}m"
        }
    }

    private fun answer(label: String) = Store.intake.firstOrNull { it.first == label }?.second ?: ""

    /** A rough "what you got back", from the setup answers. Null if we can't estimate. */
    fun gotBack(sub: String, now: Long): String? {
        val st = Store.startMs(sub)
        if (st <= 0 || st > now) return null
        val days = (now - st) / 86_400_000f
        // Only cigarettes have an estimate. Dusk never asks how much cannabis someone uses.
        if (sub != FLOW_CIGARETTE) return null
        val perDay = when (answer("Cigarettes per day")) {
            "1 to 9" -> 5f; "10 to 19" -> 15f; "20 to 29" -> 25f; "30 or more" -> 30f; else -> return null
        }
        return "≈ ${(perDay * days).toInt()} cigarettes not smoked"
    }

    /** One line for the coach, so it knows how cravings have actually been going. */
    fun summaryForCoach(): String {
        if (Store.cravings.isEmpty()) return ""
        val rate = passRate()?.let { ", pass rate $it%" } ?: ""
        val peak = peakHour()?.let { ", most often around ${"%02d".format(it)}:00" } ?: ""
        return "\nCravings logged: ${Store.cravings.size} (${passed()} passed, ${gaveIn()} gave in$rate$peak). Today: ${cravingsToday()}."
    }
}

/** The current time, refreshed every [periodMs] so counters tick. */
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private val MILESTONE_DAYS = listOf(3, 7, 14, 30)

@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Fraunces, fontSize = 30.sp, lineHeight = 34.sp)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ProgressScreen() {
    val c = MaterialTheme.colorScheme
    val now = rememberNow()
    val subs = Store.substances()
    val first = Store.firstStart()
    val started = first in 0..Store.today()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Progress", fontFamily = Fraunces, fontSize = 34.sp)

        // 1. Clear for
        if (!started) {
            Text(
                "Your tracker starts on day 1. Until then, here's what to watch for.",
                style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                subs.forEach { s ->
                    val st = Store.startMs(s)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TIcon(substanceIcon(s), size = 18.dp, tint = c.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("${flowName(s)} · clear for", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                        }
                        Text(
                            if (st in 1..now) Track.since(st, now) else "Not yet",
                            fontFamily = Fraunces, fontSize = if (subs.size > 1) 28.sp else 40.sp, lineHeight = 46.sp
                        )
                    }
                }
            }
        }

        // 2. Where you are + next milestone
        if (started) {
            TideChart(Store.tideSubstances(), LocalDate.ofEpochDay(first), here = true, nowMs = now)
            val day = Store.dayNumber()
            val next = MILESTONE_DAYS.firstOrNull { it > day }
            if (next != null) {
                val st = Store.startMs(subs.minByOrNull { Store.startOf(it).let { d -> if (d < 0) Long.MAX_VALUE else d } } ?: subs.first())
                val target = LocalDate.ofEpochDay(first + next - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val frac = if (target > st) ((now - st).toFloat() / (target - st)).coerceIn(0f, 1f) else 1f
                Magnet(Tone.Sea, 0.6f, Modifier.fillMaxWidth()) {
                    MagnetTitle(R.drawable.ic_t_flag, "Next: day $next")
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = tone(Tone.Gold).bg, trackColor = tone(Tone.Sea).fg.copy(alpha = 0.25f)
                    )
                    Text("In ${Track.since(now, target)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 3. Cravings: passed vs gave in
        val passed = Track.passed()
        val gave = Track.gaveIn()
        Magnet(Tone.Mint, -0.8f, Modifier.fillMaxWidth()) {
            MagnetTitle(R.drawable.ic_t_ripple, "Cravings")
            if (passed + gave == 0) {
                Text(
                    "Nothing logged yet. When one hits, tap the craving button, then tell Dusk whether it passed or you gave in. Both count. Honest data makes a better plan.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    BigNumber("$passed", "passed", Modifier.weight(1f))
                    BigNumber("$gave", "gave in", Modifier.weight(1f))
                    BigNumber("${Track.passRate() ?: 0}%", "pass rate", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    if (passed > 0) Box(Modifier.weight(passed.toFloat()).fillMaxHeight().background(c.primary))
                    if (gave > 0) Box(Modifier.weight(gave.toFloat()).fillMaxHeight().background(c.secondary))
                }
                Track.minutesToPass()?.let {
                    Text("A craving that passes usually takes about $it min. Long enough to feel endless. Short enough to outlast.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 4. Last 7 days
        val week = Track.week()
        if (week.any { it.second + it.third > 0 }) {
            Magnet(Tone.Sand, 0.8f, Modifier.fillMaxWidth()) {
                MagnetTitle(R.drawable.ic_t_chart_bar, "Last 7 days")
                val max = week.maxOf { it.second + it.third }.coerceAtLeast(1)
                val pass = c.primary
                val fail = c.secondary
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val slot = size.width / week.size
                    val bw = slot * 0.5f
                    val r = CornerRadius(4.dp.toPx())
                    week.forEachIndexed { i, (_, p, g) ->
                        val x = slot * i + (slot - bw) / 2
                        val hp = size.height * p / max
                        val hg = size.height * g / max
                        if (p > 0) drawRoundRect(pass, Offset(x, size.height - hp), Size(bw, hp), r)
                        if (g > 0) drawRoundRect(fail, Offset(x, size.height - hp - hg), Size(bw, hg), r)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { Text(it.first, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(pass))
                    Text("passed", style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(fail))
                    Text("gave in", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 5. When they hit
        val hours = Track.byHour()
        if (hours.sum() > 0) {
            Magnet(Tone.Coral, -0.6f, Modifier.fillMaxWidth()) {
                MagnetTitle(R.drawable.ic_t_clock, "When they hit")
                val max = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
                val ink = tone(Tone.Coral).fg
                Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                    val cell = size.width / 24
                    hours.forEachIndexed { h, n ->
                        val a = if (n == 0) 0.10f else 0.30f + 0.70f * n / max
                        drawRoundRect(ink.copy(alpha = a), Offset(h * cell + 1, 0f), Size(cell - 2, size.height), CornerRadius(3.dp.toPx()))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00", "06", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                Track.peakHour()?.let {
                    Text("Most around ${"%02d".format(it)}:00. That's the hour to plan for.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 6. Coming up
        val coming = Track.comingUp()
        val levels = Track.withdrawalToday(now)
        if (coming.isNotEmpty() || levels.isNotEmpty()) {
            Magnet(Tone.Gold, 1f, Modifier.fillMaxWidth()) {
                MagnetTitle(R.drawable.ic_t_bulb, "Coming up today")
                levels.forEach { (s, label) ->
                    Text(
                        (if (levels.size > 1) "${flowName(s)} withdrawal: " else "Withdrawal: ") + label,
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium
                    )
                }
                coming.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TIcon(R.drawable.ic_t_ripple, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (coming.isEmpty()) Text("No known triggers left today. Stay honest about the unknown ones.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // 7. Got back
        if (started) {
            val back = subs.mapNotNull { Track.gotBack(it, now) }
            Magnet(Tone.Sand, -1f, Modifier.fillMaxWidth()) {
                MagnetTitle(R.drawable.ic_t_sunset_2, "What you got back")
                back.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Text(
                    "${Store.sunsets()} sunsets kept · ${Store.totalGulls()} gulls set free · ${Store.slips.size} slips logged",
                    style = MaterialTheme.typography.bodySmall
                )
                if (back.isNotEmpty()) Text("Estimates from your setup answers.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
