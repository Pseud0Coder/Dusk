package com.dusk.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A helpline the person can call. The number is dialled through the phone app, so Dusk needs no call permission. */
private data class Helpline(val name: String, val shown: String, val dial: String, val about: String)

private val INDIA_HELPLINES = listOf(
    Helpline("Tele-MANAS", "14416", "14416", "Free mental health support, 24/7, in many languages."),
    Helpline("National Tobacco Quitline", "1800-11-2356", "1800112356", "Free, confidential help to quit tobacco.")
)

private fun dial(ctx: android.content.Context, number: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No phone app found.", Toast.LENGTH_SHORT).show()
    }
}

/** "If you need help now": emergency guidance plus helplines. Shown in Settings. */
@Composable
fun HelpSection() {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Text(
        "If you are in danger, or thinking about harming yourself, call your local emergency number now. " +
            "Dusk is not an emergency service.",
        style = MaterialTheme.typography.bodyMedium, color = c.onSurface
    )
    Text("In India", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
    INDIA_HELPLINES.forEach { h ->
        OutlinedButton(onClick = { dial(ctx, h.dial) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("${h.name}: ${h.shown}")
                Text(h.about, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** What Dusk is and isn't, how data is used, and the way to delete everything. Shown in Settings. */
@Composable
fun AboutSection(onDelete: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var showPrivacy by remember { mutableStateOf(false) }
    Text(
        "Dusk is a coach, not a medical device. It doesn't diagnose, treat, cure or prevent any medical condition. " +
            "Its replies are written by an AI and can be wrong. For medicines and for how you feel, talk to a doctor or pharmacist.",
        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant
    )
    OutlinedButton(onClick = { showPrivacy = true }, modifier = Modifier.fillMaxWidth()) { Text("How my data is used") }
    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete my data") }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("How your data is used") },
            text = { Text(PRIVACY_SUMMARY, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Close") } }
        )
    }
}

private val REPORT_REASONS = listOf(
    "Harmful or unsafe advice",
    "Inaccurate or misleading",
    "Offensive or inappropriate",
    "Something else"
)

/** Sends the reply and the reason, and nothing else about the person, to the developer's report address. */
private suspend fun postReport(url: String, reason: String, reply: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject().put("reason", reason).put("reply", reply.take(2_000)).put("app", BuildConfig.VERSION_NAME)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = conn.responseCode in 200..299
        conn.disconnect()
        ok
    }.getOrDefault(false)
}

/**
 * Report an AI reply. With a report address configured in the build the report is sent from inside the app.
 * Without one it falls back to an email draft the person can read and edit first.
 */
@Composable
fun ReportReplyDialog(reply: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var reason by remember { mutableStateOf(REPORT_REASONS[0]) }
    var sending by remember { mutableStateOf(false) }
    val inApp = BuildConfig.REPORT_URL.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Report this reply") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "What's wrong with it? Only this reply and your choice are sent to the developer. Nothing else from your phone or chat.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    REPORT_REASONS.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reason == r, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(r)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    if (inApp) {
                        sending = true
                        scope.launch {
                            val ok = postReport(BuildConfig.REPORT_URL, reason, reply)
                            sending = false
                            Toast.makeText(
                                ctx,
                                if (ok) "Thanks. Your report was sent." else "Couldn't send the report. Check your connection and try again.",
                                Toast.LENGTH_LONG
                            ).show()
                            if (ok) onDismiss()
                        }
                    } else if (BuildConfig.SUPPORT_EMAIL.isBlank()) {
                        Toast.makeText(ctx, "Reporting isn't set up in this build.", Toast.LENGTH_LONG).show()
                    } else {
                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "Dusk reply report: $reason")
                            putExtra(Intent.EXTRA_TEXT, "Reason: $reason\n\nReply:\n${reply.take(2_000)}")
                        }
                        try { ctx.startActivity(mail); onDismiss() } catch (_: ActivityNotFoundException) {
                            Toast.makeText(ctx, "No email app found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) { Text(if (sending) "Sending…" else if (inApp) "Send report" else "Write the email") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") } }
    )
}

/** People who installed before the consent screen existed see it once, before anything else. */
@Composable
fun ConsentGate() {
    val ctx = LocalContext.current
    if (!Store.onboarded || Store.consented) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Before you continue") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dusk is for adults. By continuing you confirm you're 18 or older and understand how your answers are used.")
                Text(PRIVACY_SUMMARY, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { Store.acceptConsent() }) { Text("I'm 18 or older and I agree") } },
        dismissButton = { TextButton(onClick = { (ctx as? Activity)?.finish() }) { Text("Close the app") } }
    )
}

/** Shown once to people whose old setup was cannabis only. */
@Composable
fun CannabisOnlyNote() {
    if (!Store.showCannabisOnlyNote) return
    AlertDialog(
        onDismissRequest = { Store.dismissCannabisOnlyNote() },
        title = { Text("Dusk now focuses on cigarettes") },
        text = {
            Text(
                "Dusk is now built around quitting cigarettes, with an optional cannabis add-on to the same plan. " +
                    "Your old cannabis answers were removed from this phone. You can add the cannabis option in Settings."
            )
        },
        confirmButton = { TextButton(onClick = { Store.dismissCannabisOnlyNote() }) { Text("OK") } }
    )
}
