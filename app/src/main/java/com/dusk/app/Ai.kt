package com.dusk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val until = Store.startDay - Store.today()
        val day = when {
            Store.startDay < 0 -> "Quit day not set yet."
            until > 0 -> "The quit day is in $until day(s). The person is still preparing."
            else -> "Today is day ${Store.dayNumber()} since the quit day."
        }
        val profile = if (Store.intake.isEmpty()) "" else
            "\nIntake answers (already collected in the app):\n" + Store.profileText()
        val routine = if (Store.tasks.isEmpty()) "No routine saved yet." else
            "Saved routine:\n" + Store.tasks.joinToString("\n") { t ->
                "${t.time} ${t.title}" + (if (t.id in Store.done) " (done today)" else "")
            }
        return "\n\nCurrent context\nFlow: ${flowName(Store.flow)}\nNow: $now\n$day$profile\n$routine"
    }

    /** Sends the conversation to OpenRouter and returns the assistant reply. Call from the main thread. */
    suspend fun reply(voiceOpening: String? = null): String {
        val voice = if (voiceOpening == null) "" else
            VOICE_MODE + "\nThis voice session opened with you saying: \"$voiceOpening\""
        val system = promptFor(Store.flow) + voice + context()
        val history = Store.messages.takeLast(40).toList()
        val key = Store.effectiveKey()
        val model = Store.model.ifBlank { DEFAULT_MODEL }

        return withContext(Dispatchers.IO) {
            val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val body = JSONObject().put("model", model).put("messages", msgs).toString()

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
