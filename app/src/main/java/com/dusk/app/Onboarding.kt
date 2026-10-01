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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------- Intake questions ----------

data class Q(
    val label: String,
    val text: String,
    val options: List<String> = emptyList(),
    val multi: Boolean = false,
    val freeText: Boolean = false
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

fun questionsFor(flow: String): List<Q> = if (flow == FLOW_CIGARETTE) listOf(
    Q("Cigarettes per day", "About how many cigarettes do you smoke a day?",
        listOf("1 to 9", "10 to 19", "20 to 29", "30 or more")),
    Q("First cigarette", "How soon after waking is your first one?",
        listOf("Within 30 minutes", "30 to 60 minutes", "More than an hour")),
    Q("Smoking triggers", "When do you smoke the most? Pick all that apply.",
        listOf("With coffee", "After meals", "Work breaks", "Driving", "With alcohol", "When stressed", "Around other smokers"),
        multi = true),
    Q("Stop-smoking aid", "Would you consider a stop-smoking aid, like patches or a prescription?",
        listOf("Yes", "Not sure", "No")),
    wakeQ, bedQ, noteQ
) else listOf(
    Q("How often", "How often do you use?", listOf("Every day", "Most days", "A few days a week")),
    Q("Hours high per day", "On a typical day, how many hours are you high?",
        listOf("Under 2", "2 to 5", "5 to 8", "More than 8")),
    Q("What they use", "What do you mostly use? Pick all that apply.",
        listOf("Flower", "Concentrates or vapes", "Edibles", "Mixed with tobacco"), multi = true),
    Q("When they use", "When do you use? Pick all that apply.",
        listOf("On waking", "Afternoon", "After work", "Evening", "To fall asleep"), multi = true),
    wakeQ, bedQ, noteQ
)

private fun answerOf(label: String): String = Store.intake.firstOrNull { it.first == label }?.second ?: ""

// ---------- Timeline ----------

private data class Phase(val whenText: String, val what: String)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun timeline(flow: String, day1: LocalDate): List<Phase> {
    fun d(n: Long): String = day1.plusDays(n - 1).format(dateFmt)
    return if (flow == FLOW_CIGARETTE) listOf(
        Phase(d(1), "Quit day. Cravings can start within hours."),
        Phase("${d(2)} to ${d(3)}", "Peak withdrawal. The hardest stretch."),
        Phase("${d(4)} to ${d(7)}", "Easing, but most relapses happen this week."),
        Phase(d(14), "Symptoms are fading. Cravings are rarer and shorter."),
        Phase(d(28), "Most withdrawal is behind you.")
    ) else listOf(
        Phase(d(1), "Quit day. Symptoms usually start within 1 to 3 days."),
        Phase("${d(2)} to ${d(6)}", "Peak withdrawal: irritability, poor sleep, vivid dreams, low appetite."),
        Phase("${d(7)} to ${d(14)}", "Most symptoms ease."),
        Phase(d(28), "First milestone. Brain receptors have largely recovered."),
        Phase("${d(29)} to ${d(42)}", "Sleep can still be catching up. That's normal.")
    )
}

private fun personalNotes(flow: String): List<String> {
    val notes = mutableListOf<String>()
    if (flow == FLOW_CIGARETTE) {
        val heavy = answerOf("First cigarette") == "Within 30 minutes" ||
            answerOf("Cigarettes per day") in listOf("20 to 29", "30 or more")
        if (heavy) {
            notes.add("Smoking soon after waking, or 20 or more a day, usually means stronger cravings. A pharmacist can talk you through stop-smoking aids.")
        }
        if (answerOf("Smoking triggers").contains("With alcohol")) {
            notes.add("Alcohol is a top relapse trigger in the first month. Plan around it.")
        }
    } else {
        notes.add("Withdrawal varies a lot between people, so the first week is planned tightly either way.")
        if (answerOf("When they use").contains("To fall asleep")) {
            notes.add("You use to fall asleep, so sleep is the part most likely to lag. Your evening wind-down matters most.")
        }
        if (answerOf("What they use").contains("Mixed with tobacco")) {
            notes.add("Mixing with tobacco means nicotine withdrawal too. A pharmacist can help with that part.")
        }
    }
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
        text, fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size + 6).sp,
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

// ---------- Flow ----------

@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable {
        mutableStateOf(
            when {
                Store.flow.isEmpty() -> "pick"
                Store.effectiveKey().isBlank() -> "key"
                else -> "mode"
            }
        )
    }
    var offset by rememberSaveable { mutableStateOf(1) }
    var buildNote by rememberSaveable { mutableStateOf<String?>(null) }

    when (step) {
        "pick" -> PickStep { f ->
            Store.setFlow(ctx, f)
            step = if (Store.effectiveKey().isBlank()) "key" else "mode"
        }
        "key" -> KeyStep { step = "mode" }
        "mode" -> ModeStep(onTalk = { step = "voice" }, onTap = { step = "intake" })
        "voice" -> VoiceScreen(
            opening = voiceOpeningFor(Store.flow),
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
                Store.chooseStartDay(LocalDate.now().toEpochDay() + offset)
                step = "perms"
            }
        )
        else -> PermsStep(onDone)
    }
}

@Composable
private fun PickStep(onPick: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Frame {
        Spacer(Modifier.height(24.dp))
        Heading("Quit with a plan that fits your day")
        Muted("Dusk is a coach that learns how your day works, builds a daily routine, and reminds you at the moments that matter.")
        Spacer(Modifier.height(8.dp))
        Text("What are you quitting?", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
        OutlinedButton(
            onClick = { onPick(FLOW_CIGARETTE) },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) { Text("Cigarettes", fontSize = 18.sp) }
        OutlinedButton(
            onClick = { onPick(FLOW_CANNABIS) },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) { Text("Cannabis", fontSize = 18.sp) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Dusk is a coach, not a doctor. For medicines, ask a pharmacist or doctor.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )
    }
}

@Composable
private fun ChoiceCard(title: String, body: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        border = BorderStroke(1.dp, c.primary),
        color = c.background,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.onBackground)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModeStep(onTalk: () -> Unit, onTap: () -> Unit) {
    Frame {
        Spacer(Modifier.height(24.dp))
        Heading("How do you want to set up?")
        Muted("Both end with the same plan. If typing feels like too much right now, just talk.")
        Spacer(Modifier.height(4.dp))
        ChoiceCard("Talk it through", "Say it out loud. Dusk listens and talks back.", onTalk)
        ChoiceCard("Tap through questions", "Seven quick questions, mostly one tap each.", onTap)
    }
}

@Composable
private fun KeyStep(onNext: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf(Store.apiKey) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Frame {
        Spacer(Modifier.height(24.dp))
        Heading("Connect your coach")
        Muted("Dusk's coach runs on OpenRouter. Paste a key from your OpenRouter account. It stays on this phone and goes only to OpenRouter.")
        TextButton(onClick = { uri.openUri("https://openrouter.ai/keys") }, contentPadding = PaddingValues(0.dp)) {
            Text("Get a key")
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; error = null },
            label = { Text("OpenRouter API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = c.error) }
        Button(
            onClick = {
                if (key.isBlank()) {
                    error = "Paste your key first."
                } else if (!busy) {
                    busy = true
                    error = null
                    scope.launch {
                        val problem = Ai.checkKey(key)
                        busy = false
                        if (problem == null) {
                            Store.apiKey = key.trim()
                            Store.saveSettings()
                            onNext()
                        } else {
                            error = problem
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "Testing…" else "Test key and continue") }
    }
}

@Composable
private fun IntakeStep(onFinish: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val qs = remember(Store.flow) { questionsFor(Store.flow) }
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
        LinearProgressIndicator(progress = { (qi + 1f) / qs.size }, modifier = Modifier.fillMaxWidth())
        Text("Question ${qi + 1} of ${qs.size}", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Heading(q.text, 28)

        if (q.freeText) {
            var text by remember(qi) { mutableStateOf(answer) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Gym days, night shifts, kids, a rough time of day") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { Store.setIntake(q.label, text.trim()); next() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (text.isBlank()) "Skip and build my plan" else "Build my plan") }
        } else if (q.multi) {
            val selected = answer.split(", ").filter { it.isNotBlank() }
            q.options.forEach { opt ->
                val on = opt in selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val updated = if (on) selected - opt else selected + opt
                        Store.setIntake(q.label, updated.joinToString(", "))
                        error = null
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(opt, style = MaterialTheme.typography.bodyLarge)
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
        "Reply with 2 to 3 sentences on how the plan fits me, then the routine block. Don't ask questions."

/** Asks the coach for a routine. Returns null on success, otherwise a message to show. */
private suspend fun buildPlan(note: String?): String? {
    val last = Store.messages.lastOrNull()
    if (last == null || last.role != "user") {
        Store.addMessage(Msg("user", note ?: planRequest()))
    }
    return try {
        val reply = Ai.reply()
        Store.addMessage(Msg("assistant", reply))
        if (Ai.routineIn(reply) == null) "The coach didn't return a routine. Try again." else null
    } catch (e: Exception) {
        e.message ?: "The request failed. Check your connection and try again."
    }
}

@Composable
private fun BuildStep(note: String?, onReady: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        error = buildPlan(note)
        if (error == null) onReady()
    }

    Frame {
        Spacer(Modifier.height(48.dp))
        if (error == null) {
            CircularProgressIndicator()
            Heading("Building your plan", 28)
            Muted("Fitting a routine around your day and your triggers.")
        } else {
            Heading("That didn't work", 28)
            Text(error ?: "", color = c.error)
            Button(onClick = { attempt += 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
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
    val c = MaterialTheme.colorScheme
    val reply = Store.messages.lastOrNull { it.role == "assistant" }?.content ?: ""
    val tasks = Ai.routineIn(reply) ?: emptyList()
    val explanation = Ai.display(reply)
    val day1 = LocalDate.now().plusDays(offset.toLong())

    Frame {
        Spacer(Modifier.height(8.dp))
        Heading("Your plan", 32)
        if (explanation.isNotBlank()) Muted(explanation)

        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, c.primary),
            color = c.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daily routine", style = MaterialTheme.typography.titleMedium, color = c.primary)
                tasks.forEach { t ->
                    Row {
                        Text(t.time, Modifier.width(56.dp), color = c.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        Column {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (t.note.isNotBlank()) {
                                Text(t.note, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Text("When does day 1 start?", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Today", 1 to "Tomorrow", 3 to "In 3 days").forEach { (o, label) ->
                val mod = Modifier.weight(1f)
                if (offset == o) {
                    Button(onClick = { onOffset(o) }, modifier = mod, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(label, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { onOffset(o) }, modifier = mod, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
        Text(
            if (offset == 0) "Starting today. Reminders begin right away."
            else "A set date with a little prep time works better than drifting into it. You'll get a reminder the evening before to clear out anything you'd reach for.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )

        Text("What to expect", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
        timeline(Store.flow, day1).forEach { p ->
            Row {
                Text(p.whenText, Modifier.width(112.dp), style = MaterialTheme.typography.labelLarge, color = c.primary)
                Text(p.what, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        personalNotes(Store.flow).forEach { Muted(it) }
        Text(
            "Timelines vary from person to person.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
        )

        Button(
            onClick = { if (tasks.isNotEmpty()) onApprove(tasks) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Use this routine") }
        TextButton(onClick = onRebuild, modifier = Modifier.fillMaxWidth()) { Text("Rebuild with a different mix") }
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
        Store.markOnboarded()
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
