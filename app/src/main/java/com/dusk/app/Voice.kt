package com.dusk.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private val mainHandler = Handler(Looper.getMainLooper())

// ---------- Cloud TTS (optional, picked by which key is built in) ----------

object CloudTts {
    fun engineName(): String = when {
        BuildConfig.INWORLD_KEY.isNotBlank() -> "Inworld"
        BuildConfig.FISH_KEY.isNotBlank() -> "Fish Audio"
        else -> "On-device"
    }

    /** Returns an audio file, or null to fall back to on-device speech. */
    suspend fun synthesize(ctx: Context, text: String): File? = withContext(Dispatchers.IO) {
        try {
            when {
                BuildConfig.INWORLD_KEY.isNotBlank() -> {
                    val body = JSONObject()
                        .put("text", text)
                        .put("voiceId", BuildConfig.INWORLD_VOICE)
                        .put("modelId", BuildConfig.INWORLD_MODEL)
                    val res = post(
                        "https://api.inworld.ai/tts/v1/voice",
                        "Basic ${BuildConfig.INWORLD_KEY}", body.toString(), emptyMap()
                    )
                    val b64 = JSONObject(String(res)).optString("audioContent")
                    if (b64.isBlank()) null else write(ctx, Base64.decode(b64, Base64.DEFAULT))
                }
                BuildConfig.FISH_KEY.isNotBlank() -> {
                    val body = JSONObject()
                        .put("text", text)
                        .put("format", "mp3")
                        .put("prosody", JSONObject().put("speed", 0.95))
                    if (BuildConfig.FISH_VOICE_ID.isNotBlank()) body.put("reference_id", BuildConfig.FISH_VOICE_ID)
                    write(
                        ctx,
                        post(
                            "https://api.fish.audio/v1/tts",
                            "Bearer ${BuildConfig.FISH_KEY}", body.toString(),
                            mapOf("model" to BuildConfig.FISH_MODEL)
                        )
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun post(url: String, auth: String, body: String, headers: Map<String, String>): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Authorization", auth)
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) throw Exception("TTS error $code")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun write(ctx: Context, bytes: ByteArray): File? {
        if (bytes.isEmpty()) return null
        val f = File(ctx.cacheDir, "voice_reply")
        f.writeBytes(bytes)
        return f
    }
}

// ---------- Speaking ----------

class Speaker(context: Context) {
    private val ctx = context.applicationContext
    @Volatile private var initStatus: Int? = null
    private var configured = false
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = TextToSpeech(ctx) { status -> initStatus = status }

    private fun configure(t: TextToSpeech) {
        runCatching { t.setLanguage(Locale.getDefault()) }
        runCatching { t.setSpeechRate(0.92f) }
        runCatching {
            val lang = Locale.getDefault().language
            val best = t.voices
                ?.filter { it.locale.language == lang && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                ?.maxByOrNull { it.quality }
            if (best != null) t.setVoice(best)
        }
    }

    /** Speaks the text and returns when it's finished (or cancelled). */
    suspend fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val file = CloudTts.synthesize(ctx, clean)
        if (file != null && play(file)) return
        speakLocal(clean)
    }

    private suspend fun play(file: File): Boolean = suspendCancellableCoroutine { cont ->
        val fired = AtomicBoolean(false)
        val mp = MediaPlayer()
        player = mp
        fun done(ok: Boolean) {
            if (fired.compareAndSet(false, true)) {
                runCatching { mp.release() }
                if (player === mp) player = null
                cont.resume(ok)
            }
        }
        mp.setOnCompletionListener { done(true) }
        mp.setOnErrorListener { _, _, _ -> done(false); true }
        try {
            mp.setDataSource(file.path)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            done(false)
        }
        cont.invokeOnCancellation {
            mainHandler.post {
                if (fired.compareAndSet(false, true)) {
                    runCatching { mp.stop() }
                    runCatching { mp.release() }
                }
            }
        }
    }

    private suspend fun speakLocal(text: String) {
        var waited = 0
        while (initStatus == null && waited < 4000) { delay(100); waited += 100 }
        val t = tts ?: return
        if (initStatus != TextToSpeech.SUCCESS) return
        if (!configured) { configure(t); configured = true }

        suspendCancellableCoroutine<Unit> { cont ->
            val fired = AtomicBoolean(false)
            val id = UUID.randomUUID().toString()
            fun done() { if (fired.compareAndSet(false, true)) cont.resume(Unit) }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id) done() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id) done() }
                override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done() }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id) done() }
            })
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) done()
            cont.invokeOnCancellation { runCatching { t.stop() } }
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}

// ---------- Listening ----------

data class Heard(val text: String, val error: Int? = null)

class Listener(context: Context) {
    private val ctx = context.applicationContext
    private var rec: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Listens for one turn. Must be called on the main thread. */
    suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): Heard =
        suspendCancellableCoroutine { cont ->
            val fired = AtomicBoolean(false)
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            rec = r
            var lastPartial = ""
            fun finish(h: Heard) {
                if (fired.compareAndSet(false, true)) {
                    runCatching { r.destroy() }
                    if (rec === r) rec = null
                    cont.resume(h)
                }
            }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (lastPartial.isNotBlank()) finish(Heard(lastPartial)) else finish(Heard("", error))
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    finish(Heard(t.ifBlank { lastPartial }))
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (p.isNotBlank()) { lastPartial = p; onPartial(p) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            r.startListening(intent)
            cont.invokeOnCancellation {
                mainHandler.post {
                    if (fired.compareAndSet(false, true)) {
                        runCatching { r.cancel() }
                        runCatching { r.destroy() }
                    }
                }
            }
        }

    /** Ends the current turn early; the recognizer returns what it heard so far. */
    fun stopListening() {
        runCatching { rec?.stopListening() }
    }
}

// ---------- Screen ----------

enum class VoiceState { Idle, Speaking, Listening, Thinking }

/**
 * A hands-free conversation with the coach. Dusk speaks, then listens, then replies.
 * Tap the circle to jump in while Dusk is talking, or to finish your turn early.
 * [onRoutine] is called when the coach proposes a routine (null keeps the conversation going).
 */
@Composable
fun VoiceScreen(
    opening: String,
    onRoutine: (() -> Unit)?,
    onType: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(ctx) }
    val listener = remember { Listener(ctx) }
    val recognizerOk = remember { listener.available() }

    var state by remember { mutableStateOf(VoiceState.Idle) }
    var coachLine by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var micOk by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var micDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            speaker.shutdown()
        }
    }

    fun startTurn(say: String?) {
        job?.cancel()
        job = scope.launch {
            var line = say
            hint = null
            while (isActive) {
                if (line != null) {
                    state = VoiceState.Speaking
                    coachLine = line
                    speaker.speak(line)
                }
                state = VoiceState.Listening
                heard = ""
                val h = listener.listen(onPartial = { heard = it }, onLevel = { level = it })
                level = 0f
                val text = h.text.trim()
                if (text.isEmpty()) {
                    state = VoiceState.Idle
                    val quiet = h.error == null || h.error == SpeechRecognizer.ERROR_NO_MATCH ||
                        h.error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    hint = if (quiet) "I'm here whenever you're ready. Tap the circle to talk."
                    else "I couldn't hear that. Tap the circle to try again."
                    return@launch
                }
                heard = text
                state = VoiceState.Thinking
                Store.addMessage(Msg("user", text))
                val reply = try {
                    Ai.reply(voiceOpening = opening)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state = VoiceState.Idle
                    hint = e.message ?: "Something went wrong. Tap the circle to try again."
                    return@launch
                }
                Store.addMessage(Msg("assistant", reply))
                line = Ai.display(reply).ifBlank { "Here's your plan." }
                if (onRoutine != null && Ai.routineIn(reply) != null) {
                    state = VoiceState.Speaking
                    coachLine = line
                    speaker.speak(line)
                    onRoutine()
                    return@launch
                }
            }
        }
    }

    fun tapOrb() {
        when (state) {
            VoiceState.Speaking -> { speaker.stop(); startTurn(null) }
            VoiceState.Listening -> listener.stopListening()
            VoiceState.Idle -> startTurn(null)
            VoiceState.Thinking -> {}
        }
    }

    fun leave(then: () -> Unit) {
        job?.cancel()
        speaker.stop()
        then()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micOk = granted
        micDenied = !granted
    }

    LaunchedEffect(micOk) {
        if (micOk && recognizerOk && job == null) startTurn(opening)
    }

    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheScale"
    )
    val listenScale by animateFloatAsState(1f + level * 0.2f, label = "listenScale")
    val scale = when (state) {
        VoiceState.Listening -> listenScale
        VoiceState.Thinking -> 0.95f
        else -> breathe
    }
    val status = when (state) {
        VoiceState.Idle -> "Tap the circle to talk"
        VoiceState.Speaking -> "Dusk is talking. Tap to jump in."
        VoiceState.Listening -> "Listening. Tap when you're done."
        VoiceState.Thinking -> "Thinking…"
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !recognizerOk -> {
                Spacer(Modifier.weight(1f))
                Text("Voice isn't available", fontFamily = FontFamily.Serif, fontSize = 28.sp, color = c.onBackground)
                Spacer(Modifier.height(12.dp))
                Text(
                    "This phone doesn't have a speech recognition service. You can still type.",
                    textAlign = TextAlign.Center, color = c.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
            }
            !micOk -> {
                Spacer(Modifier.weight(1f))
                Text("Talk it through", fontFamily = FontFamily.Serif, fontSize = 32.sp, color = c.onBackground)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (micDenied) "The microphone is off for Dusk. You can turn it on in your phone's app settings, or type instead."
                    else "Dusk needs the microphone to hear you. Your phone's speech service turns your voice into text, and Dusk keeps only the words.",
                    textAlign = TextAlign.Center, color = c.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                if (!micDenied) {
                    Button(
                        onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Allow microphone") }
                }
                Spacer(Modifier.weight(1f))
            }
            else -> {
                Spacer(Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(220.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(c.primaryContainer, c.primary)))
                        .clickable(onClickLabel = "Talk") { tapOrb() }
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (coachLine.isNotBlank()) {
                        Text(coachLine, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = c.onBackground)
                    }
                    if (heard.isNotBlank()) {
                        Text("You: $heard", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    hint?.let {
                        Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = c.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { leave(onType) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Type instead") }
            OutlinedButton(onClick = { leave(onExit) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("End") }
        }
    }
}
