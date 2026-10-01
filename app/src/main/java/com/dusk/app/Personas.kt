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
        "kelsey", "Kelsey", "Warm and gentle. Never judges.",
        "Warm, gentle, and reassuring. Speaks softly and slowly, names feelings kindly, and never judges.",
        Provider.Inworld, "Kelsey", listOf(Color(0xFFFFD2B0), Color(0xFFF28B6B))
    ),
    Persona(
        "jonah", "Jonah", "Calm and steady. One step at a time.",
        "Calm, steady, and grounded. Breaks things into one small practical step at a time.",
        Provider.Inworld, "Jonah", listOf(Color(0xFF9FD0D4), Color(0xFF2F7F86))
    ),
    Persona(
        "priya", "Priya", "Kind, wise, gently direct.",
        "Kind and wise, gently direct. Asks thoughtful questions and offers perspective without preaching.",
        Provider.Inworld, "Priya", listOf(Color(0xFFFFE29A), Color(0xFFE8875F))
    ),
    Persona(
        "dennis", "Dennis", "Easygoing friend, light humor.",
        "Easygoing and friendly, like a mate on a long walk. Uses light, warm humor and plain talk, never sarcasm.",
        Provider.Inworld, "Dennis", listOf(Color(0xFFB8D8E8), Color(0xFF4F7FA3))
    ),
    Persona(
        "sarah", "Sarah", "Bright and encouraging.",
        "Bright and encouraging. Notices and celebrates every small win, keeps energy up without being pushy.",
        Provider.Fish, "933563129e564b19a115bedd57b7406a", listOf(Color(0xFFF9C2C8), Color(0xFFD9776E))
    ),
    Persona(
        "adrian", "Adrian", "Laid-back. Straight talk, no lectures.",
        "Laid-back and honest. Straight talk, short sentences, no lectures, respects their choices.",
        Provider.Fish, "bf322df2096a46f18c579d0baa36f41d", listOf(Color(0xFFCFE3C8), Color(0xFF4E9A78))
    ),
    Persona(
        "nova", "Nova", "Upbeat and curious.",
        "Upbeat and curious. Gets interested in how their day works and turns plans into small experiments.",
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
    "\n\nPERSONA\nYou are speaking as $name, the person's Dusk coach. Personality: $style " +
        "Stay in this personality, but every coaching and safety rule above still applies."

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
            if (p.voiceReady()) {
                TextButton(onClick = onPreview, contentPadding = PaddingValues(0.dp)) {
                    TIcon(R.drawable.ic_t_sparkles, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (previewing) "Speaking…" else "Hear ${p.name}")
                }
            } else {
                Text(
                    "Voice needs a ${p.provider.label} key",
                    style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant
                )
            }
        }
    }
}

/** Two-column grid of personas. Tap to choose, tap "Hear" to listen first. */
@Composable
fun PersonaGrid(previewingId: String?, onPreview: (Persona) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PERSONAS.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { p ->
                    PersonaCard(
                        p, selected = Store.persona == p.id, previewing = previewingId == p.id,
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

fun previewLine(p: Persona): String = "Hi, I'm ${p.name}. Whenever a craving hits, or you just need to talk, I'm here."
