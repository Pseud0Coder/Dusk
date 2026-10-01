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
        enableEdgeToEdge()
        setContent { DuskTheme { App() } }
    }
}

enum class Screen(val label: String, val icon: Int) {
    Today("Today", R.drawable.ic_t_sunset_2),
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

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (Store.effectiveKey().isBlank()) {
            screen = Screen.Setup
            Toast.makeText(ctx, "Add your OpenRouter key first.", Toast.LENGTH_SHORT).show()
            return
        }
        Store.addMessage(Msg("user", t))
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

    if (Store.flow.isEmpty() || !Store.onboarded) {
        Box(Modifier.fillMaxSize()) {
            Onboarding(onDone = { screen = Screen.Today })
        }
        return
    }

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
                        Store.cravingFor = sub
                        screen = Screen.Coach
                        send("I'm having a ${cravingWord(sub)} craving right now.")
                    },
                    onTalk = {
                        Store.cravingFor = if (Store.flow == FLOW_BOTH) "" else Store.flow
                        voiceOpening = CRAVING_OPENING
                    },
                    onOpenCoach = { screen = Screen.Coach },
                    onSlipped = { sub ->
                        Store.recordSlip(sub)
                        screen = Screen.Coach
                        send("I slipped with ${flowName(sub).lowercase()} today.")
                    }
                )
                Screen.Coach -> CoachScreen(busy, error, onVoice = { voiceOpening = CHAT_OPENING }) { send(it) }
                Screen.Setup -> SettingsScreen()
            }
        }
    }
}

// ---------- Today ----------

private fun prepLines(flow: String): List<String> = when (flow) {
    FLOW_CIGARETTE -> listOf(
        "Throw out cigarettes, lighters, and ashtrays.",
        "If you want a stop-smoking aid, see a pharmacist this week.",
        "Tell one person your quit day."
    )
    FLOW_BOTH -> listOf(
        "Clear out cigarettes, lighters, gear, and stash.",
        "If you want a stop-smoking aid, see a pharmacist this week.",
        "Tell one person your quit days."
    )
    else -> listOf(
        "Remove gear and stash.",
        "Tell one person your quit day.",
        "Expect strange dreams for a couple of weeks. They pass."
    )
}

private val MILESTONES = listOf(3, 7, 14, 30)

private fun milestoneText(day: Int): String? = when (day) {
    3 -> "Three days. A palm just grew on your island. Time for the reward you planned."
    7 -> "One full week. The first week is the hardest, and you did it. Your island has a hut now."
    14 -> "Two weeks. For most people the worst of withdrawal is behind them. A boat just arrived."
    30 -> "Thirty days. A whole month clear. The lighthouse is lit."
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

/** Shown while a craving session is open. "It passed" sets a gull free. */
@Composable
fun CravingBanner(onTalk: () -> Unit) {
    val sub = Store.cravingFor ?: return
    val d = tone(Tone.Coral)
    val subs = if (sub.isBlank()) Store.substances() else listOf(sub)
    Surface(color = d.bg, contentColor = d.fg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TIcon(R.drawable.ic_t_ripple, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Riding out a craving", style = MaterialTheme.typography.titleMedium)
            }
            Text("Cravings usually pass within minutes. When this one does, let it go.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subs.forEach { s ->
                    Button(
                        onClick = { Store.addGull(s) },
                        colors = ButtonDefaults.buttonColors(containerColor = d.fg, contentColor = d.bg),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (subs.size > 1) "${flowName(s)} passed" else "It passed", maxLines = 1) }
                }
            }
            Row {
                TextButton(onClick = onTalk, colors = ButtonDefaults.textButtonColors(contentColor = d.fg)) { Text("Talk it through") }
                TextButton(onClick = { Store.cravingFor = null }, colors = ButtonDefaults.textButtonColors(contentColor = d.fg)) { Text("Close") }
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
    onSlipped: (String) -> Unit
) {
    LaunchedEffect(Unit) { Store.refreshDay() }
    val c = MaterialTheme.colorScheme
    val today = Store.today()
    val first = Store.firstStart()
    val prep = first > today
    val subs = Store.substances()
    var tip by rememberSaveable { mutableStateOf(0) }
    var dismissedMilestone by rememberSaveable { mutableStateOf(-1) }
    var pickCraving by remember { mutableStateOf(false) }
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
                    "This is your sky. The sun sinks as you finish your routine, and every clear day becomes a sunset you keep.",
                    "When a craving hits, tap the coral button. When it passes, you set a gull free.",
                    "Your island grows at day 3, 7, 14, and 30. Slips never take anything away."
                )
                Box(side) {
                    TipCard(tips[tip.coerceIn(0, 2)], last = tip >= 2) {
                        if (tip >= 2) Store.setTipsSeen() else tip += 1
                    }
                }
            }
        }
        if (milestone != null) {
            item { Box(side) { TipCard(milestone, last = true) { dismissedMilestone = dayN } } }
        }
        if (Store.cravingFor != null) {
            item { Box(side) { CravingBanner(onTalk) } }
        }

        item {
            Row(side, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val g = Store.totalGulls()
                StatTile(R.drawable.ic_gull, "$g", if (g == 1) "gull set free" else "gulls set free", Modifier.weight(1f))
                StatTile(R.drawable.ic_t_sunset_2, "${Store.sunsets()}", "sunsets kept", Modifier.weight(1f))
                StatTile(R.drawable.ic_t_circle_check, "$doneCount/$total", "done today", Modifier.weight(1f))
            }
        }

        item {
            Column(side, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (subs.size > 1) pickCraving = !pickCraving else onCraving(subs.first()) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.secondary, contentColor = c.onSecondary)
                ) {
                    TIcon(R.drawable.ic_t_ripple, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Craving? Ride it out", fontWeight = FontWeight.SemiBold)
                }
                if (pickCraving) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        subs.forEach { s ->
                            OutlinedButton(
                                onClick = { pickCraving = false; onCraving(s) },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) {
                                TIcon(substanceIcon(s), size = 18.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(flowName(s))
                            }
                        }
                    }
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
                        if (subs.size > 1) "Two tides at once. Each one passes." else phaseFor(Store.flow, dayN),
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
                        prepLines(Store.flow).forEach {
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
            title = { Text("That's okay") },
            text = {
                Text(
                    "Slips happen. Your sunsets, gulls, and island all stay. The day count restarts today, " +
                        "and your coach will help you work out what happened." +
                        (if (subs.size > 1) " Which one was it?" else "")
                )
            },
            confirmButton = {
                Row {
                    if (subs.size > 1) {
                        subs.forEach { s ->
                            TextButton(onClick = { slipDialog = false; onSlipped(s) }) { Text(flowName(s)) }
                        }
                    } else {
                        TextButton(onClick = { slipDialog = false; onSlipped(subs.first()) }) { Text("Restart today") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { slipDialog = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Coach ----------

@Composable
fun CoachScreen(busy: Boolean, error: String?, onVoice: () -> Unit, onSend: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Store.messages.size, busy, error) {
        listState.animateScrollToItem(Store.messages.size + 1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        if (Store.cravingFor != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { CravingBanner(onTalk = onVoice) }
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
                        if (text.isNotBlank()) Bubble(text, mine = false)
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
    var key by rememberSaveable { mutableStateOf(Store.apiKey) }
    var model by rememberSaveable { mutableStateOf(Store.model) }
    var confirmClear by remember { mutableStateOf(false) }
    var kokoroReady by remember { mutableStateOf(Kokoro.ready(ctx)) }
    var dlError by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontFamily = Fraunces, fontSize = 34.sp)

        // Quitting
        SectionTitle(substanceIcon(if (Store.flow == FLOW_CANNABIS) FLOW_CANNABIS else FLOW_CIGARETTE), "Quitting")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FLOW_CIGARETTE to "Cigarettes", FLOW_CANNABIS to "Cannabis", FLOW_BOTH to "Both").forEach { (f, label) ->
                Choice(label, Store.flow == f, Modifier.weight(1f)) { if (Store.flow != f) Store.setFlow(ctx, f) }
            }
        }
        Text(
            "Each keeps its own chat, routine, sunsets, and gulls. Reminders follow the one you pick.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )

        HorizontalDivider(color = c.outlineVariant)

        // Voice
        SectionTitle(R.drawable.ic_t_microphone, "Voice")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Kokoro", Store.voiceEngine == "kokoro", Modifier.weight(1f)) { Store.updateVoiceEngine("kokoro") }
            Choice("Phone voice", Store.voiceEngine == "phone", Modifier.weight(1f)) { Store.updateVoiceEngine("phone") }
        }
        if (Store.voiceEngine == "kokoro") {
            val p = Kokoro.progress
            when {
                kokoroReady -> {
                    Text("Pick a voice", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Kokoro.voices.forEach { v ->
                            Choice(v.name, Store.kokoroVoice == v.sid) { Store.updateKokoroVoice(v.sid) }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (!previewing) {
                                previewing = true
                                scope.launch {
                                    speaker.speak("Hi, I'm Dusk. I'm here whenever you need me.")
                                    previewing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TIcon(R.drawable.ic_t_sparkles, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (previewing) "Speaking…" else "Hear this voice")
                    }
                    TextButton(onClick = { Kokoro.delete(ctx); kokoroReady = false }) {
                        TIcon(R.drawable.ic_t_trash, size = 18.dp, tint = c.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Remove download", color = c.onSurfaceVariant)
                    }
                }
                p != null -> {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = c.secondary, trackColor = c.outlineVariant
                    )
                    Text("Downloading and unpacking, ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text(
                        "Kokoro is a free, natural voice that runs on your phone, even offline. " +
                            "It's a one-time ${Kokoro.SIZE_MB} MB download, best on Wi-Fi. Until then, Dusk uses the phone voice.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            dlError = null
                            scope.launch {
                                dlError = Kokoro.download(ctx)
                                kokoroReady = Kokoro.ready(ctx)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TIcon(R.drawable.ic_t_download, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Download Kokoro")
                    }
                    dlError?.let { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else {
            Text(
                "Using ${if (CloudTts.engineName() == "On-device") "your phone's built-in voice" else CloudTts.engineName()}.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
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

        // Coach
        SectionTitle(R.drawable.ic_t_key, "Coach")
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Your own OpenRouter key (optional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model") },
            leadingIcon = { TIcon(R.drawable.ic_t_cpu, size = 20.dp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (BuildConfig.OPENROUTER_KEY.isNotBlank()) "Leave the key empty to use the one built into the app."
            else "No key is built into this version, so add yours here.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
        Button(onClick = {
            Store.apiKey = key.trim()
            Store.model = model.trim().ifBlank { DEFAULT_MODEL }
            model = Store.model
            Store.saveSettings()
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
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
