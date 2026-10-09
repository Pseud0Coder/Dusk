package com.dusk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- Intake questions ----------

data class Q(
    val label: String,
    val text: String,
    val options: List<String> = emptyList(),
    val multi: Boolean = false,
    val freeText: Boolean = false,
    /** The cannabis add-on: yes/no, then light, medium or heavy. Nothing else is ever asked about cannabis. */
    val addon: Boolean = false
)

private val wakeQ = Q(
    "Wake-up time", "When do you usually wake up?",
    listOf("Around 6:00", "Around 7:00", "Around 8:00", "Around 9:00 or later")
)
private val bedQ = Q(
    "Bedtime", "And when do you go to bed?",
    listOf("Around 22:00", "Around 23:00", "Around midnight", "After 1:00")
)
private val noteQ = Q("Other notes", "Anything else Dusk should plan around?", freeText = true)

private val cigaretteQs = listOf(
    Q("Cigarettes per day", "About how many cigarettes do you smoke a day?",
        listOf("1 to 9", "10 to 19", "20 to 29", "30 or more")),
    Q("First cigarette", "How soon after waking is your first one?",
        listOf("Within 30 minutes", "30 to 60 minutes", "More than an hour")),
    Q("Smoking triggers", "When do you smoke the most? Pick all that apply.",
        listOf("With coffee", "After meals", "Work breaks", "Driving", "With alcohol", "When stressed", "Around other smokers"),
        multi = true),
    Q("Stop-smoking aid", "Would you consider a stop-smoking aid, like patches or a prescription?",
        listOf("Yes", "Not sure", "No")),
)

private val addonQ = Q("Cannabis", "Do you also use cannabis?", addon = true)

fun questionsFor(): List<Q> = cigaretteQs + addonQ + listOf(wakeQ, bedQ, noteQ)

private fun answerOf(label: String): String = Store.intake.firstOrNull { it.first == label }?.second ?: ""

private fun personalNotes(): List<String> {
    val notes = mutableListOf<String>()
    val heavy = answerOf("First cigarette") == "Within 30 minutes" ||
        answerOf("Cigarettes per day") in listOf("20 to 29", "30 or more")
    if (heavy) notes.add("Smoking soon after waking, or 20 or more a day, usually means stronger cravings. A pharmacist can talk you through stop-smoking aids.")
    if (answerOf("Smoking triggers").contains("With alcohol")) notes.add("Alcohol is a top relapse trigger in the first month. Plan around it.")
    if (Store.addon) notes.add("Cannabis rides along on the same quit day. Sleep is the part most likely to lag, so your evening wind-down matters.")
    return notes
}

// ---------- Building blocks ----------

@Composable
private fun Frame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun Heading(text: String, size: Int = 32) {
    Text(
        text, fontFamily = Fraunces, fontSize = size.sp, lineHeight = (size + 6).sp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun OptionButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val mod = Modifier.fillMaxWidth().height(52.dp)
    if (selected) {
        Button(onClick = onClick, modifier = mod) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = mod) { Text(text) }
    }
}

@Composable
private fun SelectCard(title: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = if (selected) c.primaryContainer else cardColor(),
        contentColor = if (selected) c.onPrimaryContainer else c.onSurface,
        shape = shape,
        border = if (selected) BorderStroke(2.dp, c.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            TIcon(icon, size = 28.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected")
        }
    }
}

@Composable
private fun ChoiceCard(title: String, body: String, icon: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = cardColor(),
        contentColor = c.onSurface,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TIcon(icon, size = 30.dp, tint = c.primary)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val mod = Modifier.weight(1f).height(52.dp)
            val pad = PaddingValues(horizontal = 6.dp)
            if (i == selected) {
                Button(onClick = { onSelect(i) }, modifier = mod, contentPadding = pad) {
                    Text(label, maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(onClick = { onSelect(i) }, modifier = mod, contentPadding = pad) {
                    Text(label, maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Three stops: light, medium, heavy. Nothing is selected until the person picks one, so the app never
 * nudges an answer. There are no numbers or frequencies on purpose.
 */
@Composable
fun LevelSlider(selected: Int, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val labels = listOf("Light", "Medium", "Heavy")
    var widthPx by remember { mutableStateOf(0) }
    val latestSelect by rememberUpdatedState(onSelect)
    fun indexAt(x: Float): Int = if (widthPx <= 0) 0 else ((x / widthPx) * 3f).toInt().coerceIn(0, 2)
    val track = c.outlineVariant
    val fill = c.primary
    val ring = c.outline
    val bg = c.background

    Column(
        Modifier.fillMaxWidth()
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) { detectTapGestures { latestSelect(indexAt(it.x)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onHorizontalDrag = { change, _ ->
                    latestSelect(indexAt(change.position.x))
                    change.consume()
                })
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val cy = size.height / 2f
            val xs = listOf(1, 3, 5).map { size.width * it / 6f }
            val stroke = 6.dp.toPx()
            drawLine(track, Offset(xs[0], cy), Offset(xs[2], cy), strokeWidth = stroke, cap = StrokeCap.Round)
            if (selected >= 0) drawLine(fill, Offset(xs[0], cy), Offset(xs[selected], cy), strokeWidth = stroke, cap = StrokeCap.Round)
            xs.forEachIndexed { i, x ->
                val on = i == selected
                val reached = i <= selected
                drawCircle(if (reached) fill else bg, radius = if (on) 15.dp.toPx() else 10.dp.toPx(), center = Offset(x, cy))
                if (!reached) drawCircle(ring, radius = 10.dp.toPx(), center = Offset(x, cy), style = Stroke(2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(selected = i == selected, role = Role.RadioButton, onClick = { onSelect(i) })
                        .semantics { stateDescription = if (i == selected) "Selected" else "Not selected" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (i == selected) c.primary else c.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The whole cannabis add-on: "Do you also use cannabis?" Yes or No, then the slider if Yes. Stores one word. */
@Composable
fun CannabisAsk(doneLabel: String, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var yes by remember {
        mutableStateOf<Boolean?>(
            when {
                Store.addon -> true
                Store.cannabis == "no" -> false
                else -> null
            }
        )
    }
    var level by remember { mutableStateOf(CANNABIS_LEVELS.indexOf(Store.cannabisLevel)) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ChipRow(listOf("Yes", "No"), when (yes) { true -> 0; false -> 1; null -> -1 }) { yes = it == 0 }
        if (yes == true) {
            Muted("How would you describe it? There's no wrong answer. This only shapes how closely your plan is paced.")
            LevelSlider(level) { level = it }
        }
        Text(
            "That's all Dusk asks about cannabis. It stays on your phone.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
        Button(
            onClick = {
                Store.chooseCannabis(if (yes == true) CANNABIS_LEVELS[level] else "no")
                onDone()
            },
            enabled = yes == false || (yes == true && level >= 0),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(doneLabel) }
    }
}

// ---------- Flow ----------

@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable {
        mutableStateOf(
            when {
                Store.flow.isEmpty() || !Store.consented -> "welcome"
                else -> "mode"
            }
        )
    }
    var offset by rememberSaveable { mutableStateOf(1) }
    var buildNote by rememberSaveable { mutableStateOf<String?>(null) }

    when (step) {
        "welcome" -> WelcomeStep {
            Store.acceptConsent()
            Store.setFlow(ctx, FLOW_CIGARETTE)
            step = "mode"
        }
        "mode" -> ModeStep(onTalk = { step = "persona" }, onTap = { step = "intake" })
        "persona" -> PersonaStep { step = "voice" }
        "voice" -> VoiceScreen(
            opening = voiceOpeningFor(),
            onRoutine = { step = "plan" },
            onType = { step = "intake" },
            onExit = { step = "mode" }
        )
        "intake" -> IntakeStep { buildNote = null; step = "build" }
        "build" -> BuildStep(buildNote) { step = "plan" }
        "plan" -> PlanStep(
            offset = offset,
            onOffset = { offset = it },
            onRebuild = {
                buildNote = "Offer a different version of the routine, in the same format."
                step = "build"
            },
            onApprove = { tasks ->
                Store.setTasks(tasks)
                Store.chooseStart(LocalDate.now().toEpochDay() + offset)
                step = "perms"
            }
        )
        "perms" -> PermsStep { step = "checkins" }
        else -> CheckinStep(onDone)
    }
}

/** What Dusk keeps and what it sends away. Shown at the first screen and under Settings. */
const val PRIVACY_SUMMARY =
    "What stays on this phone: your answers, plan, chat and progress. Dusk has no account and no server of its own.\n\n" +
    "What leaves it: to write your plan and answer you, Dusk sends your chat, your setup answers (like how many cigarettes you smoke and, " +
    "if you chose to add it, a light, medium or heavy cannabis level) and your day count to an AI service. " +
    "If you pick a coach voice, the words Dusk speaks are sent to a voice service to turn them into audio. " +
    "What you say out loud is turned into text by your phone's own speech recognition.\n\n" +
    "Dusk is a coach, not a doctor or a medical service. It doesn't diagnose or treat anything. " +
    "Talk to a doctor or pharmacist about medicines and about how you feel.\n\n" +
    "To remove everything Dusk stores, use Settings, then Delete my data, or uninstall the app."

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var agreed by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SunsetScene(
            Modifier.fillMaxWidth().height(260.dp),
            sunLevel = 0.35f, gulls = 3, island = 1, animate = !Store.reduceMotion
        )
        Column(
            Modifier.navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Heading("After dusk comes the night")
            Muted("Quitting cigarettes is hard, and some nights will be harder than the first days. Dusk tells you what's coming and helps you get ready while there's still light.")
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .selectable(selected = agreed, role = Role.Checkbox, onClick = { agreed = !agreed })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(checked = agreed, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "I'm 18 or older. I understand Dusk is a coach, not a doctor, and that what I tell it is sent to an AI service to write my plan.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onBackground
                )
            }
            TextButton(onClick = { showPrivacy = true }) { Text("How my data is used") }
            Button(
                onClick = onContinue,
                enabled = agreed,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Continue") }
        }
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("How your data is used") },
            text = { Text(PRIVACY_SUMMARY, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ModeStep(onTalk: () -> Unit, onTap: () -> Unit) {
    val count = questionsFor().size
    Frame {
        Spacer(Modifier.height(24.dp))
        Heading("How do you want to set up?")
        Muted("Both end with the same plan. If typing feels like too much right now, just talk.")
        Spacer(Modifier.height(4.dp))
        ChoiceCard("Talk it through", "Say it out loud. Dusk listens and talks back.", R.drawable.ic_t_microphone, onTalk)
        ChoiceCard("Tap through questions", "$count quick questions, mostly one tap each.", R.drawable.ic_t_hand_finger, onTap)
    }
}

@Composable
private fun PersonaStep(onNext: () -> Unit) {
    Frame {
        Spacer(Modifier.height(16.dp))
        Heading("Who would you like to talk to?")
        Muted("Same honest Dusk, different voices. Tap \"Hear\" on a few, and they'll walk you through what's ahead.")
        PersonaPicker()
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            TIcon(R.drawable.ic_t_microphone, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Start talking with ${personaById(Store.persona).name}")
        }
    }
}

@Composable
private fun IntakeStep(onFinish: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val qs = remember { questionsFor() }
    var qi by rememberSaveable { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    if (qi > qs.lastIndex) qi = 0
    BackHandler(enabled = qi > 0) { qi -= 1; error = null }

    val q = qs[qi]
    val answer = answerOf(q.label)
    fun next() {
        error = null
        if (qi == qs.lastIndex) onFinish() else qi += 1
    }

    Frame {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (qi + 1f) / qs.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = c.secondary,
            trackColor = c.outlineVariant
        )
        Text("Question ${qi + 1} of ${qs.size}", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Heading(q.text, 28)

        if (q.addon) {
            CannabisAsk(doneLabel = "Next") { next() }
        } else if (q.freeText) {
            var text by remember(qi) { mutableStateOf(answer) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Gym days, night shifts, kids, a rough time of day") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    // Free text goes to the coach as data. Hostile or off-topic notes are dropped, and long ones cut.
                    val note = text.trim().take(300).let { if (Guard.blockedLocally(it)) "" else it }
                    Store.setIntake(q.label, note)
                    next()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (text.isBlank()) "Skip and build my plan" else "Build my plan") }
        } else if (q.multi) {
            val selected = answer.split(", ").filter { it.isNotBlank() }
            q.options.forEach { opt ->
                val on = opt in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        val updated = if (on) selected - opt else selected + opt
                        Store.setIntake(q.label, updated.joinToString(", "))
                        error = null
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(opt, style = MaterialTheme.typography.bodyLarge, color = c.onBackground)
                }
            }
            error?.let { Text(it, color = c.error) }
            Button(
                onClick = { if (selected.isEmpty()) error = "Pick at least one." else next() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Next") }
        } else {
            q.options.forEach { opt ->
                OptionButton(opt, selected = answer == opt) {
                    Store.setIntake(q.label, opt)
                    next()
                }
            }
        }
    }
}

private fun planRequest(): String =
    "Here are my intake answers:\n" + Store.profileText() +
        "\n\nBuild my daily routine. My quit day isn't fixed yet, so plan for day 1. " +
        "Reply with at most 2 short sentences on how the plan fits me, then the routine block. Don't ask questions."

/** Asks the coach for a routine. Returns null on success, otherwise a message to show. */
private suspend fun buildPlan(note: String?): String? {
    val last = Store.messages.lastOrNull()
    if (last == null || last.role != "user") {
        Store.addMessage(Msg("user", note ?: planRequest(), trusted = true))
    }
    return try {
        val reply = Ai.reply()
        Store.addMessage(Msg("assistant", reply))
        if (Ai.routineIn(reply) == null) "The coach didn't return a routine. Try again." else null
    } catch (e: Exception) {
        e.message ?: "The request failed. Check your connection and try again."
    }
}

private val buildCaptions = listOf(
    "Mapping your triggers", "Finding your swaps", "Placing your reminders", "Marking your milestones"
)

@Composable
private fun BuildStep(note: String?, onReady: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var caption by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        error = buildPlan(note)
        if (error == null) onReady()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            caption = (caption + 1) % buildCaptions.size
        }
    }
    val sun = if (Store.reduceMotion) 0.5f else rememberInfiniteTransition(label = "sun").animateFloat(
        initialValue = 0.9f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sink"
    ).value

    Column(Modifier.fillMaxSize()) {
        SunsetScene(
            Modifier.fillMaxWidth().weight(1f),
            sunLevel = sun, gulls = 3, island = 1, animate = !Store.reduceMotion
        )
        Column(
            Modifier.fillMaxWidth().background(cardColor()).navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (error == null) {
                Text(buildCaptions[caption], fontFamily = Fraunces, fontSize = 26.sp, textAlign = TextAlign.Center, color = c.onSurface)
                Text("Fitting a routine around your day.", color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                Text("That didn't work", fontFamily = Fraunces, fontSize = 26.sp, color = c.onSurface)
                Text(error ?: "", color = c.error, textAlign = TextAlign.Center)
                Button(onClick = { attempt += 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun PlanStep(
    offset: Int,
    onOffset: (Int) -> Unit,
    onRebuild: () -> Unit,
    onApprove: (List<Task>) -> Unit
) {
    val reply = Store.messages.lastOrNull { it.role == "assistant" }?.content ?: ""
    val tasks = Ai.routineIn(reply) ?: emptyList()
    val explanation = Ai.display(reply)
    val day1 = LocalDate.now().plusDays(offset.toLong())
    val quitLines = listOf((if (Store.addon) "Cigarettes and cannabis" else "Cigarettes") to day1)
    val offsets = listOf(0, 1, 3)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Heading("Your plan")
        if (explanation.isNotBlank()) Muted(explanation)

        QuitTiles(quitLines)

        // People who set up by voice haven't been asked about the add-on yet. Two taps, then the plan can use it.
        if (Store.cannabis.isEmpty()) {
            Text("Do you also use cannabis?", style = MaterialTheme.typography.titleMedium)
            CannabisAsk(doneLabel = "Save") { }
        }

        Text("When does day 1 start?", style = MaterialTheme.typography.titleMedium)
        ChipRow(listOf("Today", "Tomorrow", "In 3 days"), offsets.indexOf(offset)) { onOffset(offsets[it]) }

        TideChart(Store.tideSubstances(), day1)
        SwapMagnet(tasks)
        DayStrip(tasks)
        RoutineMagnet(tasks)
        personalNotes().forEachIndexed { i, n -> HeadsUp(n, if (i % 2 == 0) 1.2f else -1f) }

        Text(
            "Timelines vary from person to person.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { if (tasks.isNotEmpty()) onApprove(tasks) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Use this routine") }
        TextButton(onClick = onRebuild, modifier = Modifier.fillMaxWidth()) { Text("Rebuild with a different mix") }
    }
}

@Composable
private fun CheckinStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var freq by rememberSaveable { mutableStateOf(2) }
    var ongoing by rememberSaveable { mutableStateOf(false) }
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Frame {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TIcon(R.drawable.ic_t_bell, size = 28.dp, tint = c.primary)
            Spacer(Modifier.width(12.dp))
            Heading("Dusk can check in on you", 28)
        }
        Muted("In your first two weeks, Dusk can send a short question before your hard moments. Something like: \"Your after-dinner moment is coming up around 19:45. What's your plan for it?\"")
        Muted("You can check in with one tap, talk it through, or ignore it. Ignoring it costs you nothing.")
        Text("How often?", style = MaterialTheme.typography.titleMedium)
        ChipRow(listOf("Off", "1 a day", "2 a day", "3 a day"), freq) { freq = it }
        if (freq > 0) {
            Text("For how long?", style = MaterialTheme.typography.titleMedium)
            ChipRow(listOf("First 2 weeks", "Ongoing"), if (ongoing) 1 else 0) { ongoing = it == 1 }
        }
        if (!notifOk && freq > 0) {
            Text(
                "Check-ins arrive as notifications, which are off for Dusk right now. You can turn them on in your phone's app settings.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
        }
        Button(
            onClick = {
                Store.updateCheckins(freq, ongoing)
                Store.markOnboarded()
                Reminders.rescheduleAll(ctx)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (freq == 0) "No check-ins" else "Sounds good") }
        Text(
            "Change this or turn it off anytime in Settings.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
    }
}

@Composable
private fun PermsStep(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var stage by rememberSaveable { mutableStateOf("ask") }
    val first = Store.tasks.firstOrNull()
    val needsAsk = Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun finish() {
        Reminders.rescheduleAll(ctx)
        onDone()
    }

    fun afterPermission() {
        if (Build.VERSION.SDK_INT in 31..32 && !Reminders.canExact(ctx)) stage = "exact" else finish()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission() else stage = "denied"
    }

    LaunchedEffect(Unit) { if (!needsAsk && stage == "ask") afterPermission() }

    Frame {
        Spacer(Modifier.height(24.dp))
        when (stage) {
            "exact" -> {
                Heading("One more setting")
                Muted("Allow alarms so reminders arrive on time instead of a few minutes late.")
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Open setting") }
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
            }
            "denied" -> {
                Heading("Reminders are off")
                Muted("Dusk still works as a checklist. You can turn reminders on later in your phone's app settings.")
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
            }
            else -> {
                Heading("Turn on reminders")
                Muted("Reminders are how the routine works when you're not in the app." +
                    (if (first != null) " Your first one is at ${first.time}: ${first.title}." else ""))
                Text(
                    "If your phone stops them, set Dusk's battery usage to Unrestricted in app settings.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (needsAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else afterPermission()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Allow reminders") }
                TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
            }
        }
    }
}
