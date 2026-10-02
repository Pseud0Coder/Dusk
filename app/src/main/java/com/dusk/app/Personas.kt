package com.dusk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Provider(val label: String) { Inworld("Inworld"), Fish("Fish Audio") }

/**
 * A coach you can talk to: a personality the coach takes on in chat and voice,
 * plus a natural voice from Inworld or Fish Audio.
 */
data class Persona(
    val id: String,
    val name: String,
    val vibe: String,
    val style: String,
    val provider: Provider,
    val voiceId: String,
    val colors: List<Color>,
)

val PERSONAS = listOf(
    Persona(
        "kelsey", "Kelsey", "Gentle voice, honest words.",
        "Gentle in tone, honest in substance. Delivers hard truths softly, never waters them down, never judges.",
        Provider.Inworld, "Kelsey", listOf(Color(0xFFFFD2B0), Color(0xFFF28B6B))
    ),
    Persona(
        "jonah", "Jonah", "Calm. Plain. One step at a time.",
        "Calm and steady. Lays out what's coming plainly, then gives one small practical step.",
        Provider.Inworld, "Jonah", listOf(Color(0xFF9FD0D4), Color(0xFF2F7F86))
    ),
    Persona(
        "priya", "Priya", "Asks what you're avoiding.",
        "Wise and direct. Asks the question they're avoiding and offers perspective without preaching.",
        Provider.Inworld, "Priya", listOf(Color(0xFFFFE29A), Color(0xFFE8875F))
    ),
    Persona(
        "dennis", "Dennis", "Dry humor. Never sugarcoats.",
        "Easygoing, like a mate on a long walk. Dry humor and plain talk. Never sugarcoats, never sarcastic about their struggle.",
        Provider.Inworld, "Dennis", listOf(Color(0xFFB8D8E8), Color(0xFF4F7FA3))
    ),
    Persona(
        "sarah", "Sarah", "Warm energy, no cheerleading.",
        "Warm energy but no cheerleading. Names real progress plainly, then names the next hard part.",
        Provider.Fish, "933563129e564b19a115bedd57b7406a", listOf(Color(0xFFF9C2C8), Color(0xFFD9776E))
    ),
    Persona(
        "adrian", "Adrian", "Blunt. No lectures.",
        "Blunt and laid-back. Short sentences, no lectures, no hype. Respects their choices.",
        Provider.Fish, "bf322df2096a46f18c579d0baa36f41d", listOf(Color(0xFFCFE3C8), Color(0xFF4E9A78))
    ),
    Persona(
        "nova", "Nova", "Treats hard moments as problems.",
        "Curious and practical. Treats hard moments as problems to solve, not drama, and turns plans into small experiments.",
        Provider.Fish, "b545c585f631496c914815291da4e893", listOf(Color(0xFFFFD8A8), Color(0xFFF0765A))
    ),
)

fun personaById(id: String): Persona = PERSONAS.firstOrNull { it.id == id } ?: PERSONAS.first()

/** Whether this persona's voice provider has a key built into the app. */
fun Persona.voiceReady(): Boolean = when (provider) {
    Provider.Inworld -> BuildConfig.INWORLD_KEY.isNotBlank()
    Provider.Fish -> BuildConfig.FISH_KEY.isNotBlank()
}

/** The coach prompt block that gives the coach this persona's personality. */
fun Persona.promptBlock(): String =
    "\n\nPERSONA\nYou are speaking as $name, one of Dusk's voices. Personality: $style " +
        "This is a flavor of Dusk, not a replacement: stay honest, no hype, and every coaching and safety rule above still applies."

@Composable
fun PersonaAvatar(p: Persona, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(p.colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(p.name.take(1), fontFamily = Fraunces, fontSize = (size.value * 0.45f).sp, color = Color(0xFF1F3A4D))
    }
}

@Composable
fun PersonaCard(
    p: Persona,
    selected: Boolean,
    previewing: Boolean,
    previewLabel: String,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = if (selected) c.primaryContainer else cardColor(),
        contentColor = if (selected) c.onPrimaryContainer else c.onSurface,
        shape = shape,
        border = if (selected) BorderStroke(2.dp, c.primary) else null,
        modifier = modifier.clip(shape).clickable(onClick = onSelect)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(p, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(p.provider.label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
            Text(p.vibe, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPreview, contentPadding = PaddingValues(0.dp)) {
                TIcon(R.drawable.ic_t_sparkles, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(if (previewing) previewLabel else "Hear ${p.name}")
            }
            if (!p.voiceReady()) {
                Text(
                    "Phone voice until a ${p.provider.label} key is added",
                    style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant
                )
            }
        }
    }
}

/** Two-column grid of personas. Tap to choose, tap "Hear" to listen first. */
@Composable
fun PersonaGrid(previewingId: String?, previewLabel: String, onPreview: (Persona) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PERSONAS.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { p ->
                    PersonaCard(
                        p, selected = Store.persona == p.id, previewing = previewingId == p.id, previewLabel = previewLabel,
                        onSelect = { Store.updatePersona(p.id) },
                        onPreview = { onPreview(p) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** What each part of the spoken introduction covers, in order. Every claim matches what the app does. */
val INTRO_TOPICS = listOf(
    "what Dusk is: an honest coach, not a cheerleader. Quitting is hard and Dusk won't pretend otherwise. After dusk comes the night, and Dusk is the last light to get ready by",
    "the plan: Dusk learns how their day works and puts small swaps right before their usual triggers, with reminders that have a Done button",
    "the first days: withdrawal is loudest early, then eases. Cravings come in waves that usually pass within minutes; tap the craving button and ride it out, and each one that passes sets a gull free in their sky",
    "the night: after the loud days, the harder part is quieter. Motivation fades, stress returns, and just one starts to sound reasonable. Dusk helps them prepare for that before it comes",
    "progress, said plainly: every clear day becomes a sunset they keep, and their island grows at days 3, 7, 14, and 30. Not trophies, just proof of what they got through",
    "slips: a slip is information, not a verdict. The day count restarts, but sunsets, gulls, and the island stay, and Dusk helps work out what led to it",
    "talking: they can type, or just talk out loud when typing feels like too much",
    "care: Dusk is a coach, not a doctor, and will point them to a pharmacist or doctor for medicines or if things get hard",
    "an invitation to pick the voice they want beside them on the hard nights, and begin",
)

val INTRO_FALLBACKS = listOf(
    "I'll be straight with you. This is hard, and I won't pretend otherwise. After dusk comes the night. I'm here to help you get ready for it.",
    "I learn how your day works, then put small swaps right before the moments you'd usually reach for it.",
    "The first days are the loudest. Cravings come in waves and most pass within minutes. Ride one out and a gull goes free.",
    "Then it gets quiet, and quiet is harder. Motivation fades and just one starts to sound reasonable. We prepare for that now.",
    "Every clear day becomes a sunset you keep. Not a trophy. Proof of what you got through.",
    "If you slip, it's information, not a verdict. Your count restarts. Everything you earned stays.",
    "You can type, or just talk to me when typing feels like too much.",
    "I'm a coach, not a doctor. For medicines, or if it gets too heavy, I'll point you to someone who can help.",
    "Pick the voice you want beside you on the hard nights. Then let's begin.",
)

/** Session-wide progress through the introduction, shared by every preview. */
object Intro {
    var next by mutableStateOf(0)
    val spoken = mutableStateListOf<String>()
}

/**
 * The coach selector. Tapping "Hear" on a coach has DeepSeek write the next part
 * of Dusk's introduction in that coach's personality, then speaks it in their voice.
 */
@Composable
fun PersonaPicker() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    var busyId by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf<Triple<Persona, Int, String>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PersonaGrid(busyId, if (writing) "Getting ready…" else "Speaking…") { p ->
            if (busyId == null) {
                busyId = p.id
                writing = true
                scope.launch {
                    val part = Intro.next % INTRO_TOPICS.size
                    val line = Ai.introLine(p, part, Intro.spoken.takeLast(2))
                    Intro.spoken.add(line)
                    Intro.next = part + 1
                    caption = Triple(p, part, line)
                    writing = false
                    speaker.speak(line, p)
                    busyId = null
                }
            }
        }
        caption?.let { (p, part, line) ->
            Surface(
                color = tone(Tone.Mint).bg, contentColor = tone(Tone.Mint).fg,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    PersonaAvatar(p, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${p.name} · part ${part + 1} of ${INTRO_TOPICS.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (caption == null) {
            Text(
                "Tap \"Hear\" on any coach. Each one you try tells you the next part of how Dusk works.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
            )
        }
    }
}
