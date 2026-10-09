package com.dusk.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Reminders.ensureChannel(this)
        handleCheckin(intent)
        enableEdgeToEdge()
        setContent { DuskTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCheckin(intent)
    }

    /** Opened from a check-in notification: remember what the person tapped. */
    private fun handleCheckin(i: Intent?) {
        i?.getStringExtra("widget")?.let {
            Store.pendingWidget = it
            i.removeExtra("widget")
        }
        val mode = i?.getStringExtra("checkin") ?: return
        Store.pendingQuestion = i.getStringExtra("question") ?: ""
        Store.pendingCheckin = mode
        i.removeExtra("checkin")
        getSystemService(android.app.NotificationManager::class.java).cancel(Checkins.NOTIF_ID)
    }
}

enum class Screen(val label: String, val icon: Int) {
    Today("Today", R.drawable.ic_t_sunset_2),
    Progress("Progress", R.drawable.ic_t_chart_bar),
    Coach("Coach", R.drawable.ic_t_message_circle),
    Setup("Settings", R.drawable.ic_t_adjustments_horizontal),
}

private fun cravingWord(sub: String) = if (sub == FLOW_CIGARETTE) "cigarette" else "cannabis"

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var voiceOpening by rememberSaveable { mutableStateOf<String?>(null) }
    var quickCheckin by remember { mutableStateOf(false) }

    fun send(text: String, trusted: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (Store.effectiveKey().isBlank()) {
            error = "The coach isn't available in this version of the app."
            return
        }
        Store.addMessage(Msg("user", t, trusted))
        busy = true
        error = null
        scope.launch {
            try {
                Store.addMessage(Msg("assistant", Ai.reply()))
            } catch (e: Exception) {
                error = e.message ?: "The request failed. Check your connection and send again."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Store.pendingWidget, Store.onboarded) {
        if (!Store.onboarded || Store.pendingWidget != "craving") return@LaunchedEffect
        Store.pendingWidget = null
        voiceOpening = null
        Store.startCraving(FLOW_CIGARETTE)
        screen = Screen.Coach
        send("I'm having a craving right now.", trusted = true)
    }

    LaunchedEffect(Store.pendingCheckin, Store.onboarded) {
        if (!Store.onboarded) return@LaunchedEffect
        when (Store.pendingCheckin) {
            "talk" -> {
                if (Store.pendingQuestion.isNotBlank()) Store.addMessage(Msg("assistant", Store.pendingQuestion))
                voiceOpening = null
                screen = Screen.Coach
                Store.pendingCheckin = null
            }
            "quick" -> {
                voiceOpening = null
                screen = Screen.Today
                quickCheckin = true
                Store.pendingCheckin = null
            }
        }
    }

    if (Store.flow.isEmpty() || !Store.onboarded) {
        Box(Modifier.fillMaxSize()) {
            Onboarding(onDone = { screen = Screen.Today })
        }
        return
    }

    ConsentGate()
    CannabisOnlyNote()

    val opening = voiceOpening
    if (opening != null) {
        Box(Modifier.fillMaxSize()) {
            VoiceScreen(
                opening = opening,
                onRoutine = { voiceOpening = null; screen = Screen.Coach },
                onType = { voiceOpening = null; screen = Screen.Coach },
                onExit = { voiceOpening = null }
            )
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(containerColor = cardColor(), tonalElevation = 0.dp) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { TIcon(s.icon, size = 24.dp) },
                        label = { Text(s.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (screen) {
                Screen.Today -> TodayScreen(
                    onCraving = { sub ->
                        Store.startCraving(sub)
                        screen = Screen.Coach
                        send("I'm having a ${cravingWord(sub)} craving right now.", trusted = true)
                    },
                    onTalk = {
                        Store.startCraving(FLOW_CIGARETTE)
                        voiceOpening = CRAVING_OPENING
                    },
                    onOpenCoach = { screen = Screen.Coach },
                    onAdjustCheckins = { screen = Screen.Setup },
                    onSlipped = { sub ->
                        Store.recordSlip(sub)
                        screen = Screen.Coach
                        send("I slipped with ${flowName(sub).lowercase()} today.", trusted = true)
                    }
                )
                Screen.Coach -> CoachScreen(
                    busy, error,
                    onVoice = { voiceOpening = CHAT_OPENING },
                    onGaveIn = { sub ->
                        Store.recordSlip(sub)
                        send("I gave in to a ${cravingWord(sub)} craving.", trusted = true)
                    }
                ) { send(it) }
                Screen.Progress -> ProgressScreen()
                Screen.Setup -> SettingsScreen()
            }
        }
    }

    if (quickCheckin) {
        val p = personaById(Store.persona)
        AlertDialog(
            onDismissRequest = { quickCheckin = false },
            icon = { PersonaAvatar(p, 40.dp) },
            title = { Text(p.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Store.pendingQuestion.ifBlank { "How's it going, honestly?" })
                    OutlinedButton(onClick = {
                        quickCheckin = false
                        Store.logCheckin("Steady")
                        Toast.makeText(ctx, "Good. Keep the routine. Steady days build the next ones.", Toast.LENGTH_LONG).show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        TIcon(R.drawable.ic_t_circle_check, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Steady")
                    }
                    OutlinedButton(onClick = {
                        quickCheckin = false
                        Store.logCheckin("Struggling")
                        screen = Screen.Coach
                        send("It's hard today.", trusted = true)
                    }, modifier = Modifier.fillMaxWidth()) {
                        TIcon(R.drawable.ic_t_wave_sine, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("It's hard today")
                    }
                    OutlinedButton(onClick = {
                        quickCheckin = false
                        Store.logCheckin("Craving")
                        Store.startCraving(FLOW_CIGARETTE)
                        screen = Screen.Coach
                        send("I'm having a craving right now.", trusted = true)
                    }, modifier = Modifier.fillMaxWidth()) {
                        TIcon(R.drawable.ic_t_ripple, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Craving right now")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { quickCheckin = false }) { Text("Not now") } }
        )
    }
}

/** One-time offer for people who set up Dusk before check-ins existed. */
@Composable
fun CheckinOffer(onAdjust: () -> Unit) {
    val ctx = LocalContext.current
    val d = tone(Tone.Mint)
    Surface(color = d.bg, contentColor = d.fg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TIcon(R.drawable.ic_t_bell, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Dusk can check in on you", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "In your first two weeks, Dusk can send a short question before your hard moments. " +
                    "Check in with one tap, talk it through, or ignore it. Off unless you say yes.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { Store.updateCheckins(2, false); Checkins.schedule(ctx) },
                    modifier = Modifier.weight(1f)
                ) { Text("Yes, 2 a day", maxLines = 1) }
                OutlinedButton(
                    onClick = { Store.updateCheckins(0, false); Checkins.schedule(ctx) },
                    modifier = Modifier.weight(1f)
                ) { Text("No thanks", maxLines = 1) }
            }
            TextButton(
                onClick = { Store.updateCheckins(Store.checkinFreq, Store.checkinOngoing); onAdjust() },
                colors = ButtonDefaults.textButtonColors(contentColor = d.fg),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Choose how often in Settings") }
        }
    }
}

// ---------- Today ----------

private fun prepLines(): List<String> = listOf(
    "Throw out cigarettes, lighters, and ashtrays.",
    "If you want a stop-smoking aid, see a pharmacist this week."
) + (if (Store.addon) listOf("Clear out anything that's a cue, and plan your evenings. Expect light sleep and strange dreams for a couple of weeks. They pass.") else emptyList()) +
    listOf("Tell one person your quit day.")

private val MILESTONES = listOf(3, 7, 14, 30)

private fun milestoneText(day: Int): String? = when (day) {
    3 -> "Day 3. Your body's protest is at its loudest around now. It can't stay this loud. A palm grew on your island."
    7 -> "One week. The physical part is easing. The mental part is just starting to argue. Your island has a hut."
    14 -> "Two weeks. This is where people relax and slip. Keep the routine anyway. A boat arrived."
    30 -> "Thirty days. The cravings will still visit. Now you know how to answer the door. The lighthouse is lit."
    else -> null
}

@Composable
fun TipCard(text: String, last: Boolean, onNext: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.primaryContainer, contentColor = c.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                TIcon(R.drawable.ic_t_bulb, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(
                onClick = onNext, contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = c.onPrimaryContainer)
            ) { Text(if (last) "Got it" else "Next") }
        }
    }
}

@Composable
fun MilestoneRow(day: Int) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MILESTONES.forEach { m ->
            val reached = day >= m
            val fg = if (reached) c.onPrimary else c.onSurface
            Row(
                Modifier
                    .background(if (reached) c.primary else cardColor(), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TIcon(R.drawable.ic_t_flag, size = 14.dp, tint = fg)
                Spacer(Modifier.width(4.dp))
                Text("$m", style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

@Composable
fun StatTile(icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TIcon(icon, size = 22.dp, tint = c.primary)
            Spacer(Modifier.width(6.dp))
            Text(value, fontFamily = Fraunces, fontSize = 28.sp, lineHeight = 32.sp)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
    }
}

/** Shown while a craving session is open. "It passed" sets a gull free. With the cannabis add-on, one tap picks which. */
@Composable
fun CravingBanner(onTalk: () -> Unit, onGaveIn: (String) -> Unit) {
    val sub = Store.cravingFor ?: return
    val d = tone(Tone.Coral)
    Surface(color = d.bg, contentColor = d.fg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TIcon(R.drawable.ic_t_ripple, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Riding out a craving", style = MaterialTheme.typography.titleMedium)
            }
            if (Store.addon) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FLOW_CIGARETTE, FLOW_CANNABIS).forEach { s ->
                        FilterChip(
                            selected = sub == s,
                            onClick = { Store.cravingFor = s },
                            label = { Text(flowName(s)) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = d.fg,
                                selectedContainerColor = d.fg,
                                selectedLabelColor = d.bg
                            )
                        )
                    }
                }
            }
            Text("It'll feel endless. It isn't. Most pass within minutes. When this one does, let it go.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { Store.addGull(sub) },
                colors = ButtonDefaults.buttonColors(containerColor = d.fg, contentColor = d.bg),
                modifier = Modifier.fillMaxWidth()
            ) { Text("It passed", maxLines = 1) }
            OutlinedButton(
                onClick = { Store.resolveCraving(sub, "gave_in"); onGaveIn(sub) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = d.fg),
                border = BorderStroke(1.dp, d.fg.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("I gave in", maxLines = 1) }
            Row {
                TextButton(onClick = onTalk, colors = ButtonDefaults.textButtonColors(contentColor = d.fg)) { Text("Talk it through") }
                TextButton(onClick = { Store.cravingFor = null }, colors = ButtonDefaults.textButtonColors(contentColor = d.fg)) { Text("Later") }
            }
        }
    }
}

@Composable
fun RoutineTile(t: Task, done: Boolean, open: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val k = kindDuo(t.kind)
    val shape = RoundedCornerShape(20.dp)
    Surface(color = k.bg, contentColor = k.fg, shape = shape, modifier = modifier.clip(shape).clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TIcon(kindIcon(t.kind), size = 18.dp, tint = k.fg)
                Spacer(Modifier.width(6.dp))
                Text(t.time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (done) k.fg else Color.Transparent)
                        .border(2.dp, k.fg, CircleShape)
                        .clickable(onClickLabel = if (done) "Mark not done" else "Mark done", onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = k.bg, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                t.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = if (open) 4 else 2
            )
            if (open && t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TodayScreen(
    onCraving: (String) -> Unit,
    onTalk: () -> Unit,
    onOpenCoach: () -> Unit,
    onAdjustCheckins: () -> Unit,
    onSlipped: (String) -> Unit
) {
    LaunchedEffect(Unit) { Store.refreshDay() }
    val c = MaterialTheme.colorScheme
    val now = rememberNow()
    val today = Store.today()
    val first = Store.firstStart()
    val prep = first > today
    val subs = Store.substances()
    var tip by rememberSaveable { mutableStateOf(0) }
    var dismissedMilestone by rememberSaveable { mutableStateOf(-1) }
    var slipDialog by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf(-1) }
    val dayN = Store.dayNumber()
    val milestone = if (!prep && first >= 0 && dismissedMilestone != dayN) milestoneText(dayN) else null
    val total = Store.tasks.size
    val doneCount = Store.done.count { id -> Store.tasks.any { it.id == id } }
    val sunLevel = if (total == 0) 0.75f else 1f - doneCount.toFloat() / total
    val side = Modifier.padding(horizontal = 20.dp)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                SunsetScene(
                    Modifier.matchParentSize(),
                    sunLevel = sunLevel, gulls = Store.totalGulls(), island = Store.islandLevel(),
                    animate = !Store.reduceMotion
                )
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    when {
                        first < 0 -> {
                            Text("Pick your day one", fontFamily = Fraunces, fontSize = 30.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { Store.startToday() }) { Text("Start day 1 today") }
                        }
                        prep -> {
                            val until = (first - today).toInt()
                            Text("Day 1 starts", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (until == 1) "Tomorrow" else "In $until days",
                                fontFamily = Fraunces, fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.Light
                            )
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            subs.forEach { s ->
                                val st = Store.startOf(s)
                                val waiting = st > today
                                Column {
                                    Text(
                                        if (waiting) "${flowName(s)}, from" else "${flowName(s)}, day",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        if (waiting) LocalDate.ofEpochDay(st).format(DateTimeFormatter.ofPattern("d MMM"))
                                        else "${Store.dayOf(s)}",
                                        fontFamily = Fraunces,
                                        fontSize = if (subs.size > 1) 52.sp else 88.sp,
                                        lineHeight = if (subs.size > 1) 58.sp else 92.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                    val st = Store.startMs(s)
                                    if (!waiting && st in 1..now) {
                                        Text("Clear for ${Track.since(st, now)}", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!Store.tipsSeen) {
            item {
                val tips = listOf(
                    "The sun sets as you work through today's routine. Every day you get through becomes a sunset you keep.",
                    "When a craving hits, tap the coral button. It'll feel endless. It isn't. When it passes, a gull goes free.",
                    "Your island grows at day 3, 7, 14, and 30. A slip restarts the count. It never takes anything away."
                )
                Box(side) {
                    TipCard(tips[tip.coerceIn(0, 2)], last = tip >= 2) {
                        if (tip >= 2) Store.setTipsSeen() else tip += 1
                    }
                }
            }
        }
        if (!Store.checkinAsked) {
            item { Box(side) { CheckinOffer(onAdjustCheckins) } }
        }
        if (milestone != null) {
            item { Box(side) { TipCard(milestone, last = true) { dismissedMilestone = dayN } } }
        }
        if (Store.cravingFor != null) {
            item { Box(side) { CravingBanner(onTalk, onSlipped) } }
        }

        item {
            Row(side, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val g = Store.totalGulls()
                StatTile(R.drawable.ic_gull, "$g", if (g == 1) "gull set free" else "gulls set free", Modifier.weight(1f))
                StatTile(R.drawable.ic_t_sunset_2, "${Store.sunsets()}", "sunsets kept", Modifier.weight(1f))
                StatTile(R.drawable.ic_t_circle_check, "$doneCount/$total", "done today", Modifier.weight(1f))
            }
        }

        val nextCraving = if (first in 0..today) Track.comingUp().firstOrNull() else null
        if (nextCraving != null) {
            item {
                val g = tone(Tone.Gold)
                Surface(color = g.bg, contentColor = g.fg, shape = RoundedCornerShape(18.dp), modifier = side.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TIcon(R.drawable.ic_t_bulb, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Heads up", style = MaterialTheme.typography.labelMedium)
                            Text(nextCraving, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Column(side, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCraving(FLOW_CIGARETTE) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.secondary, contentColor = c.onSecondary)
                ) {
                    TIcon(R.drawable.ic_t_ripple, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Craving? Ride it out", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onTalk, modifier = Modifier.fillMaxWidth()) {
                    TIcon(R.drawable.ic_t_microphone, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Rather talk it through? Use voice")
                }
            }
        }

        if (!prep && first >= 0) {
            item {
                Column(side, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        phaseFor(dayN),
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant
                    )
                    MilestoneRow(dayN)
                }
            }
        }

        if (prep) {
            item {
                Box(side) {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TIcon(R.drawable.ic_t_checklist, size = 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Before day 1", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        prepLines().forEach {
                            Text("\u2022  $it", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                        }
                        TextButton(onClick = { Store.startToday() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Start day 1 today instead")
                        }
                    }
                }
            }
        }

        item {
            Row(side, verticalAlignment = Alignment.CenterVertically) {
                TIcon(R.drawable.ic_t_list_check, size = 22.dp, tint = c.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (prep) "Your routine, from day 1" else "Today's routine", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (Store.tasks.isEmpty()) {
            item {
                Column(side) {
                    Text("No routine yet. Your coach will build one around your day.", color = c.onSurfaceVariant)
                    TextButton(onClick = onOpenCoach, contentPadding = PaddingValues(0.dp)) { Text("Talk to your coach") }
                }
            }
        } else {
            items(Store.tasks.chunked(2)) { pair ->
                Row(side.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        RoutineTile(
                            t, t.id in Store.done, openId == t.id,
                            onToggle = { Store.toggleDone(t.id) },
                            onOpen = { openId = if (openId == t.id) -1 else t.id },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        val sunsets = Store.sunsets()
        if (sunsets > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(side, verticalAlignment = Alignment.CenterVertically) {
                        TIcon(R.drawable.ic_t_sunset_2, size = 22.dp, tint = c.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Your sunsets", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(sunsets.coerceAtMost(120)) { SunsetTile(it) }
                    }
                }
            }
        }

        if (first in 0..today) {
            item {
                TextButton(onClick = { slipDialog = true }, modifier = side.fillMaxWidth()) {
                    TIcon(R.drawable.ic_t_refresh, size = 18.dp, tint = c.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("I slipped", color = c.onSurfaceVariant)
                }
            }
        }
    }

    if (slipDialog) {
        AlertDialog(
            onDismissRequest = { slipDialog = false },
            title = { Text("It happened. Now what?") },
            text = {
                Text(
                    "A slip is information, not a verdict. Your day count restarts today. Your sunsets, gulls, and island stay. " +
                        "Next, your coach helps you find what led to it, so the plan gets better."
                )
            },
            confirmButton = {
                TextButton(onClick = { slipDialog = false; onSlipped(FLOW_CIGARETTE) }) { Text("Restart today") }
            },
            dismissButton = { TextButton(onClick = { slipDialog = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Coach ----------

@Composable
fun CoachScreen(busy: Boolean, error: String?, onVoice: () -> Unit, onGaveIn: (String) -> Unit, onSend: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var input by rememberSaveable { mutableStateOf("") }
    var reporting by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Store.messages.size, busy, error) {
        listState.animateScrollToItem(Store.messages.size + 1)
    }

    reporting?.let { ReportReplyDialog(it) { reporting = null } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        if (Store.cravingFor != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { CravingBanner(onTalk = onVoice, onGaveIn = onGaveIn) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Bubble(greetingFor(Store.flow), mine = false) }
            itemsIndexed(Store.messages) { _, m ->
                if (m.role == "user") {
                    Bubble(m.content, mine = true)
                } else {
                    val text = Ai.display(m.content)
                    val routine = Ai.routineIn(m.content)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (text.isNotBlank()) {
                            Bubble(text, mine = false)
                            TextButton(
                                onClick = { reporting = text },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                TIcon(R.drawable.ic_t_flag, size = 14.dp, tint = c.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text("Report this reply", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                            }
                        }
                        if (routine != null) RoutineCard(routine) {
                            Store.setTasks(routine)
                            Reminders.rescheduleAll(ctx)
                            Toast.makeText(ctx, "Routine saved. Daily reminders are set.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item {
                when {
                    busy -> Text("Thinking…", color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    error != null -> Text(error, color = c.error, modifier = Modifier.padding(start = 4.dp))
                    else -> Spacer(Modifier.height(1.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onVoice, modifier = Modifier.padding(bottom = 4.dp)) {
                TIcon(R.drawable.ic_t_microphone, size = 24.dp, tint = c.primary, desc = "Talk")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk it through") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor(),
                    unfocusedContainerColor = cardColor()
                )
            )
            IconButton(
                onClick = { onSend(input); input = "" },
                enabled = !busy && input.isNotBlank()
            ) { TIcon(R.drawable.ic_t_send, size = 24.dp, tint = c.primary, desc = "Send") }
        }
    }
}

@Composable
fun Bubble(text: String, mine: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) c.primaryContainer else cardColor(),
            contentColor = if (mine) c.onPrimaryContainer else c.onSurface,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 20.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun RoutineCard(tasks: List<Task>, onUse: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, c.primary),
        color = cardColor(),
        contentColor = c.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Proposed routine", style = MaterialTheme.typography.titleMedium, color = c.primary)
            tasks.forEach { t ->
                val k = kindDuo(t.kind)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(k.fg))
                    Spacer(Modifier.width(8.dp))
                    Text(t.time, Modifier.width(50.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    Text(t.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("Use this routine") }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SectionTitle(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TIcon(icon, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 8.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = pad) { Text(label, maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = pad) { Text(label, maxLines = 1) }
    }
}

@Composable
private fun IconAction(icon: Int, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        TIcon(icon, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontFamily = Fraunces, fontSize = 34.sp)

        // Quitting
        SectionTitle(R.drawable.ic_t_smoking, "Quitting cigarettes")
        Text(
            "Dusk is built around quitting cigarettes. Cannabis is an optional add-on to the same plan and the same quit day.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
        var addonOn by remember { mutableStateOf(Store.addon) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TIcon(R.drawable.ic_t_cannabis, size = 22.dp, tint = c.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Also quitting cannabis", style = MaterialTheme.typography.titleMedium)
                Text("Only a yes or no and a light, medium or heavy choice. Nothing else is asked.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Switch(checked = addonOn, onCheckedChange = { on ->
                addonOn = on
                if (!on) Store.chooseCannabis("no")
            })
        }
        if (addonOn) {
            LevelSlider(CANNABIS_LEVELS.indexOf(Store.cannabisLevel)) { Store.chooseCannabis(CANNABIS_LEVELS[it]) }
        }

        HorizontalDivider(color = c.outlineVariant)

        // Coach persona and voice
        SectionTitle(R.drawable.ic_t_users, "Your coach")
        Text(
            "Pick who you talk to. Their personality shapes how the coach writes and speaks. Tap \"Hear\" and each coach tells you a part of how Dusk works.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
        PersonaPicker()
        if (PERSONAS.any { !it.voiceReady() }) {
            Text(
                "Coaches without a voice key still chat and talk, using your phone's built-in voice.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
        }

        HorizontalDivider(color = c.outlineVariant)

        // Check-ins
        SectionTitle(R.drawable.ic_t_bell, "Check-ins from Dusk")
        Text(
            "Dusk can send a short question before your hard moments, timed around the triggers in your routine. " +
                "Check in with one tap, talk it through, or ignore it.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Off", 1 to "1 a day", 2 to "2 a day", 3 to "3 a day").forEach { (n, label) ->
                Choice(label, Store.checkinFreq == n, Modifier.weight(1f)) {
                    Store.updateCheckins(n, Store.checkinOngoing)
                    Checkins.schedule(ctx)
                }
            }
        }
        if (Store.checkinFreq > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("First 2 weeks", !Store.checkinOngoing, Modifier.weight(1f)) {
                    Store.updateCheckins(Store.checkinFreq, false); Checkins.schedule(ctx)
                }
                Choice("Ongoing", Store.checkinOngoing, Modifier.weight(1f)) {
                    Store.updateCheckins(Store.checkinFreq, true); Checkins.schedule(ctx)
                }
            }
            Text(
                "Around ${Checkins.times().joinToString(", ")}." +
                    (if (Checkins.active()) "" else " They start on day 1."),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
            IconAction(R.drawable.ic_t_message_circle, "Send a sample check-in") {
                Checkins.show(ctx, Checkins.times().firstOrNull() ?: "18:30")
            }
        }

        HorizontalDivider(color = c.outlineVariant)

        // Themes, coming next
        SectionTitle(R.drawable.ic_t_palette, "Themes")
        Text(
            "Coming next: complete themes that change the whole design, not just the colors. Island sunset is the default.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )

        HorizontalDivider(color = c.outlineVariant)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TIcon(R.drawable.ic_t_wind, size = 22.dp, tint = c.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Reduce motion", style = MaterialTheme.typography.titleMedium)
                Text("Keeps the sky, gulls, and sun still.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Switch(checked = Store.reduceMotion, onCheckedChange = { Store.updateReduceMotion(it) })
        }

        IconAction(R.drawable.ic_t_bell, "Send a test notification") {
            Reminders.show(ctx, 9_999, "Dusk", "Reminders are working.", withDone = false)
        }
        if (!Reminders.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
            IconAction(R.drawable.ic_t_clock, "Allow on-time reminders") {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
        IconAction(R.drawable.ic_t_refresh, "Redo setup questions") { Store.redoOnboarding() }
        IconAction(R.drawable.ic_t_trash, "Clear chat") { confirmClear = true }

        HorizontalDivider(color = c.outlineVariant)
        SectionTitle(R.drawable.ic_t_bell, "If you need help now")
        HelpSection()

        HorizontalDivider(color = c.outlineVariant)
        SectionTitle(R.drawable.ic_t_flag, "About Dusk")
        AboutSection(onDelete = { confirmDelete = true })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete my data?") },
            text = { Text("This removes your answers, chat, routine, progress and settings from this phone and starts Dusk from the beginning. It can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Store.wipeAll(ctx) }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("Your coach will forget this conversation. Your routine, sunsets, and gulls stay.") },
            confirmButton = { TextButton(onClick = { Store.clearChat(); confirmClear = false }) { Text("Clear chat") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
