package com.dusk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------- Theme ----------

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5D7C), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4EE), onPrimaryContainer = Color(0xFF14324A),
    secondary = Color(0xFFE0A040), onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFFD6E4EE),
    background = Color(0xFFF3F5F7), onBackground = Color(0xFF1C2630),
    surface = Color(0xFFF3F5F7), onSurface = Color(0xFF1C2630),
    surfaceVariant = Color(0xFFE3E8ED), onSurfaceVariant = Color(0xFF55636F),
    outline = Color(0xFFB7C2CB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC2DE), onPrimary = Color(0xFF0F2A3D),
    primaryContainer = Color(0xFF24445C), onPrimaryContainer = Color(0xFFD6E4EE),
    secondary = Color(0xFFE2AE5C), onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF24445C),
    background = Color(0xFF151C22), onBackground = Color(0xFFE4EAEF),
    surface = Color(0xFF151C22), onSurface = Color(0xFFE4EAEF),
    surfaceVariant = Color(0xFF222C35), onSurfaceVariant = Color(0xFFA3B1BC),
    outline = Color(0xFF3A4752),
)

@Composable
fun DuskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

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

enum class Screen(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Home),
    Coach("Coach", Icons.Filled.Face),
    Setup("Settings", Icons.Filled.Settings),
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (Store.apiKey.isBlank()) {
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
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Onboarding(onDone = { screen = Screen.Today })
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = null) },
                        label = { Text(s.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (screen) {
                Screen.Today -> TodayScreen(
                    onCraving = { screen = Screen.Coach; send("I'm having a craving right now.") },
                    onOpenCoach = { screen = Screen.Coach }
                )
                Screen.Coach -> CoachScreen(busy, error) { send(it) }
                Screen.Setup -> SettingsScreen()
            }
        }
    }
}

// ---------- Today ----------


private fun prepLines(flow: String): List<String> = if (flow == FLOW_CIGARETTE) listOf(
    "Throw out cigarettes, lighters, and ashtrays before day 1.",
    "If you want a stop-smoking aid, see a pharmacist or doctor this week.",
    "Tell one person your quit day.",
    "Decide what you'll do at each usual smoking time."
) else listOf(
    "Remove gear and stash before day 1.",
    "Tell one person your quit day.",
    "Plan something for each usual time you use.",
    "Expect poor sleep and strange dreams for a couple of weeks. It passes."
)

@Composable
fun TipCard(text: String, last: Boolean, onNext: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.primaryContainer, contentColor = c.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onNext, contentPadding = PaddingValues(0.dp)) { Text(if (last) "Got it" else "Next") }
        }
    }
}

@Composable
fun TodayScreen(onCraving: () -> Unit, onOpenCoach: () -> Unit) {
    LaunchedEffect(Unit) { Store.refreshDay() }
    val c = MaterialTheme.colorScheme
    val prep = Store.startDay > Store.today()
    val daysUntil = (Store.startDay - Store.today()).toInt()
    var tip by rememberSaveable { mutableStateOf(0) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!Store.tipsSeen) {
            item {
                val tips = listOf(
                    if (prep) "This is your countdown. The day counter starts on day 1."
                    else "This is your day counter. It grows every day you stay clear.",
                    "Your routine lives below. Check items off as you go, or tap Done on a reminder.",
                    "Tap the craving button when it hits. Dusk helps you through the next few minutes."
                )
                TipCard(tips[tip.coerceIn(0, 2)], last = tip >= 2) {
                    if (tip >= 2) Store.setTipsSeen() else tip += 1
                }
            }
        }
        item {
            Column {
                when {
                    Store.startDay < 0 -> {
                        Text("Pick your day one.", fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp, color = c.onBackground)
                        Spacer(Modifier.height(8.dp))
                        Text("The counter starts when you do.", color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { Store.startToday() }) { Text("Start day 1 today") }
                    }
                    prep -> {
                        Text("${flowName(Store.flow)}, day 1 starts", color = c.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (daysUntil == 1) "Tomorrow" else "In $daysUntil days",
                            fontFamily = FontFamily.Serif, fontSize = 56.sp, lineHeight = 64.sp,
                            fontWeight = FontWeight.Light, color = c.primary
                        )
                        Text(
                            LocalDate.ofEpochDay(Store.startDay).format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                            color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Before day 1", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                        Spacer(Modifier.height(4.dp))
                        prepLines(Store.flow).forEach {
                            Text("\u2022  $it", color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                        }
                        TextButton(onClick = { Store.startToday() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Start day 1 today instead")
                        }
                    }
                    else -> {
                        Text("${flowName(Store.flow)}, day", color = c.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Store.dayNumber()}",
                            fontFamily = FontFamily.Serif, fontSize = 120.sp, lineHeight = 120.sp,
                            fontWeight = FontWeight.Light, color = c.primary
                        )
                        Text(phaseFor(Store.flow, Store.dayNumber()), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onCraving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.secondary, contentColor = c.onSecondary)
                ) { Text("I'm craving right now", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(24.dp))
                if (Store.tasks.isNotEmpty()) {
                    Text(
                        if (prep) "Your routine, from day 1"
                        else "Today ${Store.done.count { id -> Store.tasks.any { it.id == id } }} of ${Store.tasks.size}",
                        style = MaterialTheme.typography.titleMedium, color = c.onBackground
                    )
                }
            }
        }

        if (Store.tasks.isEmpty()) {
            item {
                Text("No routine yet. Your coach will build one around your day.", color = c.onSurfaceVariant)
                TextButton(onClick = onOpenCoach, contentPadding = PaddingValues(0.dp)) { Text("Talk to your coach") }
            }
        } else {
            items(Store.tasks, key = { it.id }) { t ->
                TaskRow(t, t.id in Store.done) { Store.toggleDone(t.id) }
                HorizontalDivider(color = c.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun TaskRow(t: Task, done: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t.time, Modifier.width(60.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) c.onSurfaceVariant else c.onBackground,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
            if (t.note.isNotBlank()) {
                Text(t.note, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        Checkbox(checked = done, onCheckedChange = { onToggle() })
    }
}

// ---------- Coach ----------

@Composable
fun CoachScreen(busy: Boolean, error: String?, onSend: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Store.messages.size, busy, error) {
        listState.animateScrollToItem(Store.messages.size + 1)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
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
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk it through") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = { onSend(input); input = "" },
                enabled = !busy && input.isNotBlank()
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

@Composable
fun Bubble(text: String, mine: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) c.primaryContainer else c.surfaceVariant,
            contentColor = if (mine) c.onPrimaryContainer else c.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun RoutineCard(tasks: List<Task>, onUse: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.primary),
        color = c.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Proposed routine", style = MaterialTheme.typography.titleMedium, color = c.primary)
            tasks.forEach { t ->
                Row {
                    Text(t.time, Modifier.width(56.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
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
fun SettingsScreen() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var key by rememberSaveable { mutableStateOf(Store.apiKey) }
    var model by rememberSaveable { mutableStateOf(Store.model) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", fontFamily = FontFamily.Serif, fontSize = 34.sp, color = c.onBackground)

        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("OpenRouter API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Your key is stored only on this phone.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        Button(onClick = {
            Store.apiKey = key.trim()
            Store.model = model.trim().ifBlank { DEFAULT_MODEL }
            model = Store.model
            Store.saveSettings()
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Save") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Quitting", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(FLOW_CIGARETTE, FLOW_CANNABIS).forEach { f ->
                if (Store.flow == f) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(flowName(f)) }
                } else {
                    OutlinedButton(onClick = { Store.setFlow(ctx, f) }, modifier = Modifier.weight(1f)) { Text(flowName(f)) }
                }
            }
        }
        Text(
            "Each keeps its own chat, routine and day count. Reminders follow the one you pick.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        OutlinedButton(onClick = {
            Reminders.show(ctx, 9_999, "Dusk", "Reminders are working.", withDone = false)
        }, modifier = Modifier.fillMaxWidth()) { Text("Send a test notification") }

        if (!Reminders.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }, modifier = Modifier.fillMaxWidth()) { Text("Allow on-time reminders") }
        }

        OutlinedButton(onClick = {
            Store.startToday()
            Toast.makeText(ctx, "Day 1 is now today.", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Restart day 1 from today") }

        OutlinedButton(onClick = { Store.redoOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text("Redo setup questions") }

        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear chat") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("Your coach will forget this conversation. Your routine stays.") },
            confirmButton = { TextButton(onClick = { Store.clearChat(); confirmClear = false }) { Text("Clear chat") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
