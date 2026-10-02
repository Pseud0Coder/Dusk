package com.dusk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Ai {
    private val routineRx = Regex("```routine\\s*([\\s\\S]*?)```")

    fun routineIn(text: String): List<Task>? =
        routineRx.find(text)
            ?.let { m -> runCatching { parseTasks(m.groupValues[1].trim()) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

    fun display(text: String): String = routineRx.replace(text, "").trim()

    private fun context(): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))
        val today = Store.today()
        val day = Store.substances().joinToString("\n") { s ->
            val st = Store.startOf(s)
            when {
                st < 0 -> "${flowName(s)}: quit day not set yet."
                st > today -> "${flowName(s)}: quit day is in ${st - today} day(s), still preparing."
                else -> "${flowName(s)}: day ${today - st + 1} since quitting."
            }
        }
        val profile = if (Store.intake.isEmpty()) "" else
            "\nIntake answers (already collected in the app):\n" + Store.profileText()
        val routine = if (Store.tasks.isEmpty()) "No routine saved yet." else
            "Saved routine:\n" + Store.tasks.joinToString("\n") { t ->
                "${t.time} ${t.title}" + (if (t.id in Store.done) " (done today)" else "")
            }
        val checkin = if (Store.lastCheckin.isBlank()) "" else "\nLast check-in: ${Store.lastCheckin}"
        return "\n\nCurrent context\nFlow: ${flowName(Store.flow)}\nNow: $now\n$day$profile$checkin\n$routine"
    }

    /** Sends the conversation to OpenRouter and returns the assistant reply. Call from the main thread. */
    suspend fun reply(voiceOpening: String? = null): String {
        val voice = if (voiceOpening == null) "" else
            VOICE_MODE + "\nThis voice session opened with you saying: \"$voiceOpening\""
        val system = promptFor(Store.flow) + personaById(Store.persona).promptBlock() + voice + context()
        return complete(system, Store.messages.takeLast(40).toList(), fast = voiceOpening != null)
    }

    /**
     * Writes one part of the spoken introduction to Dusk, in [p]'s personality.
     * Falls back to a ready-made line if the model is slow or unreachable.
     */
    suspend fun introLine(p: Persona, part: Int, earlier: List<String>): String {
        val topic = INTRO_TOPICS[part]
        val system = "You write short spoken lines for the Dusk app's voice preview. You are ${p.name}, one of Dusk's coaches. " +
            "Personality: ${p.style}\n" +
            "Rules: 1 or 2 short sentences, 25 words at most in total. Natural spoken English, warm and calm. " +
            "No lists, emoji, markdown, or quotation marks. Only describe what you're told Dusk does. No medical advice."
        val quitting = if (Store.flow.isBlank()) "smoking" else flowName(Store.flow).lowercase()
        val user = "The person is quitting $quitting. This is part ${part + 1} of ${INTRO_TOPICS.size} of a spoken introduction to Dusk. " +
            "Different coaches take turns: each coach they tap reads the next part. " +
            (if (part == 0) "Open with a short greeting and your name. " else "Say your name in a few words, then carry on. ") +
            (if (earlier.isNotEmpty()) "Earlier parts said: ${earlier.joinToString(" ")} " else "") +
            "Your part should cover: $topic. Reply with only the line to speak."
        if (Store.effectiveKey().isBlank()) return fallbackIntro(p, part)
        val line = withTimeoutOrNull(15_000) {
            runCatching { complete(system, listOf(Msg("user", user)), fast = true) }.getOrNull()
        }
        return line?.trim()?.trim('"')?.takeIf { it.isNotBlank() && it.length < 400 } ?: fallbackIntro(p, part)
    }

    private fun fallbackIntro(p: Persona, part: Int): String =
        "I'm ${p.name}. " + INTRO_FALLBACKS[part]

    /** Set once if the model refuses to skip its thinking step, so we stop asking. */
    @Volatile private var reasoningMandatory = false

    /**
     * Sends one conversation to OpenRouter and returns the reply text.
     * [fast] skips the model's thinking step for short spoken lines, which cuts most of the wait.
     */
    private suspend fun complete(system: String, turns: List<Msg>, fast: Boolean = false): String {
        if (fast && !reasoningMandatory) {
            try {
                return completeOnce(system, turns, skipThinking = true)
            } catch (e: Exception) {
                if (e.message?.contains("mandatory", ignoreCase = true) == true ||
                    e.message?.contains("reasoning", ignoreCase = true) == true
                ) reasoningMandatory = true else throw e
            }
        }
        return completeOnce(system, turns, skipThinking = false)
    }

    private suspend fun completeOnce(system: String, turns: List<Msg>, skipThinking: Boolean): String {
        val key = Store.effectiveKey()
        val model = DEFAULT_MODEL

        return withContext(Dispatchers.IO) {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            turns.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val req = JSONObject().put("model", model).put("messages", msgs)
            if (skipThinking) req.put("reasoning", JSONObject().put("effort", "none")).put("max_tokens", 200)
            val body = req.toString()

            val c = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 20_000
                c.readTimeout = 120_000
                c.doOutput = true
                c.setRequestProperty("Authorization", "Bearer $key")
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("X-Title", "Dusk")
                c.outputStream.use { it.write(body.toByteArray()) }

                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw Exception(errorMessage(code, text))

                val message = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val content = if (message.isNull("content")) "" else message.getString("content").trim()
                if (content.isEmpty()) throw Exception("The model sent an empty reply. Send your message again.")
                content
            } finally {
                c.disconnect()
            }
        }
    }

    /** Returns null if the key works, otherwise a message to show the person. */
    suspend fun checkKey(key: String): String? = withContext(Dispatchers.IO) {
        val c = URL("https://openrouter.ai/api/v1/auth/key").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("Authorization", "Bearer ${key.trim()}")
            val code = c.responseCode
            if (code in 200..299) null
            else if (code == 401 || code == 403) "OpenRouter rejected that key. Check it and try again."
            else "OpenRouter returned error $code. Try again in a moment."
        } catch (e: Exception) {
            "Couldn't reach OpenRouter. Check your connection and try again."
        } finally {
            c.disconnect()
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
            ?: body.take(200)
        return when (code) {
            401 -> "OpenRouter rejected the API key. Check it in Settings."
            402 -> "Your OpenRouter account is out of credits."
            429 -> "Too many requests. Wait a moment and send again."
            else -> "Error $code: $msg"
        }
    }
}
