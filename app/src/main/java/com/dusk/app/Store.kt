package com.dusk.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Msg(val role: String, val content: String)
data class Task(
    val id: Int, val time: String, val title: String,
    val note: String = "", val kind: String = "", val replaces: String = ""
)

val KINDS = setOf("body", "mind", "food", "sleep", "social")

const val DEFAULT_MODEL = "deepseek/deepseek-v4.1-flash"
const val FLOW_CIGARETTE = "cigarette"
const val FLOW_CANNABIS = "cannabis"
const val FLOW_BOTH = "both"

fun flowName(flow: String) = when (flow) {
    FLOW_CIGARETTE -> "Cigarettes"
    FLOW_CANNABIS -> "Cannabis"
    FLOW_BOTH -> "Cigarettes and cannabis"
    else -> ""
}

private val timeRx = Regex("^(\\d{1,2}):(\\d{2})$")

/** Parses a JSON array of {time, title, note}. Invalid items are skipped; ids are 1..n in time order. */
fun parseTasks(json: String): List<Task> {
    val a = JSONArray(json)
    val out = mutableListOf<Task>()
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        val m = timeRx.find(o.optString("time").trim()) ?: continue
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) continue
        val title = if (o.isNull("title")) "" else o.optString("title").trim()
        if (title.isEmpty()) continue
        val note = if (o.isNull("note")) "" else o.optString("note").trim()
        val kind = if (o.isNull("kind")) "" else o.optString("kind").trim().lowercase()
        val replaces = if (o.isNull("replaces")) "" else o.optString("replaces").trim().take(40)
        out.add(Task(0, "%02d:%02d".format(h, min), title, note, if (kind in KINDS) kind else "", replaces))
    }
    return out.sortedBy { it.time }.mapIndexed { i, t -> t.copy(id = i + 1) }
}

/**
 * App state. Each flow (cigarette, cannabis) keeps its own chat, routine,
 * day count and checklist. Only the active flow's routine has reminders.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private var loaded = false

    var apiKey by mutableStateOf("")
    var model by mutableStateOf(DEFAULT_MODEL)
    var flow by mutableStateOf("")
    var startDay by mutableStateOf(-1L)
    /** Cannabis quit day when quitting both (cigarettes use startDay). */
    var startDay2 by mutableStateOf(-1L)
    var banked by mutableStateOf(0)
    private var gullTick by mutableStateOf(0)
    /** Which substance the current craving is about, while a craving session is open. "" means not specified. */
    var cravingFor by mutableStateOf<String?>(null)
    val messages = mutableStateListOf<Msg>()
    val tasks = mutableStateListOf<Task>()
    val done = mutableStateListOf<Int>()
    private var doneDay = -1L
    var onboarded by mutableStateOf(false)
    var tipsSeen by mutableStateOf(false)
    var reduceMotion by mutableStateOf(false)
    /** Which coach persona (personality + voice) the person picked. */
    var persona by mutableStateOf("kelsey")
    val intake = mutableStateListOf<Pair<String, String>>()

    private fun k(name: String) = "${name}_$flow"

    fun init(ctx: Context) {
        if (loaded) return
        prefs = ctx.applicationContext.getSharedPreferences("dusk", Context.MODE_PRIVATE)
        apiKey = prefs.getString("key", "") ?: ""
        model = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        flow = prefs.getString("flow", "") ?: ""
        tipsSeen = prefs.getBoolean("tips_seen", false)
        reduceMotion = prefs.getBoolean("reduce_motion", false)
        persona = prefs.getString("persona", "kelsey") ?: "kelsey"
        loadFlow()
        loaded = true
    }

    private fun loadFlow() {
        messages.clear(); tasks.clear(); done.clear(); intake.clear()
        onboarded = false
        startDay = -1L; startDay2 = -1L; banked = 0; doneDay = -1L
        if (flow.isEmpty()) return

        startDay = prefs.getLong(k("start"), -1L)
        startDay2 = prefs.getLong(k("start2"), -1L)
        banked = prefs.getInt(k("banked"), 0)
        val ma = JSONArray(prefs.getString(k("msgs"), "[]"))
        for (i in 0 until ma.length()) {
            val o = ma.getJSONObject(i)
            messages.add(Msg(o.getString("r"), o.getString("c")))
        }
        tasks.addAll(runCatching { parseTasks(prefs.getString(k("tasks"), "[]") ?: "[]") }.getOrDefault(emptyList()))
        doneDay = prefs.getLong(k("doneDay"), -1L)
        if (doneDay == today()) {
            val da = JSONArray(prefs.getString(k("done"), "[]"))
            for (i in 0 until da.length()) done.add(da.getInt(i))
        }
        val ia = JSONArray(prefs.getString(k("intake"), "[]"))
        for (i in 0 until ia.length()) {
            val o = ia.getJSONArray(i)
            intake.add(o.getString(0) to o.getString(1))
        }
        onboarded = prefs.getBoolean(k("onboarded"), false)
        // People who set the app up before onboarding existed skip it.
        if (!onboarded && !prefs.contains(k("onboarded")) && (tasks.isNotEmpty() || messages.isNotEmpty())) {
            onboarded = true
            tipsSeen = true
            prefs.edit().putBoolean(k("onboarded"), true).putBoolean("tips_seen", true).apply()
        }
    }

    /** Switches the active flow and moves reminders to that flow's routine. */
    fun setFlow(ctx: Context, f: String) {
        if (f == flow) return
        flow = f
        prefs.edit().putString("flow", f).apply()
        loadFlow()
        Reminders.rescheduleAll(ctx)
    }

    /** The person's own key if they set one in Settings, otherwise the key built into the app. */
    fun effectiveKey(): String = apiKey.trim().ifEmpty { BuildConfig.OPENROUTER_KEY }

    fun today(): Long = LocalDate.now().toEpochDay()
    /** Days since the first quit day (day 1 = the quit day). 0 if not started. */
    fun dayNumber(): Int = firstStart().let { if (it < 0) 0 else (today() - it + 1).toInt() }

    fun substances(): List<String> = when (flow) {
        FLOW_BOTH -> listOf(FLOW_CIGARETTE, FLOW_CANNABIS)
        "" -> emptyList()
        else -> listOf(flow)
    }

    fun startOf(sub: String): Long = if (flow == FLOW_BOTH && sub == FLOW_CANNABIS) startDay2 else startDay

    fun dayOf(sub: String): Int = startOf(sub).let { if (it < 0) 0 else (today() - it + 1).toInt() }

    fun firstStart(): Long = substances().map { startOf(it) }.filter { it >= 0 }.minOrNull() ?: -1L

    fun chooseStart(sub: String, day: Long) {
        if (flow == FLOW_BOTH && sub == FLOW_CANNABIS) {
            startDay2 = day
            prefs.edit().putLong(k("start2"), day).apply()
        } else {
            startDay = day
            prefs.edit().putLong(k("start"), day).apply()
        }
    }

    fun gulls(sub: String): Int {
        @Suppress("UNUSED_VARIABLE") val tick = gullTick
        return prefs.getInt(k("gulls_$sub"), 0)
    }

    fun totalGulls(): Int = substances().sumOf { gulls(it) }

    /** A craving was ridden out: one more gull in the sky. */
    fun addGull(sub: String) {
        prefs.edit().putInt(k("gulls_$sub"), gulls(sub) + 1).apply()
        gullTick += 1
        cravingFor = null
    }

    private fun liveSunsets(): Int {
        val f = firstStart()
        return if (f < 0) 0 else (today() - f).toInt().coerceAtLeast(0)
    }

    /** One sunset for every completed day since quitting. Never goes down, even after a slip. */
    fun sunsets(): Int = banked + liveSunsets()

    /** A slip restarts that substance's day count but keeps every sunset, gull and island piece. */
    fun recordSlip(sub: String) {
        val before = sunsets()
        chooseStart(sub, today())
        banked = (before - liveSunsets()).coerceAtLeast(0)
        prefs.edit().putInt(k("banked"), banked).apply()
    }

    /** 0 bare island, 1 palm (3 sunsets), 2 hut (7), 3 boat (14), 4 lighthouse (30). */
    fun islandLevel(): Int = sunsets().let {
        when {
            it >= 30 -> 4
            it >= 14 -> 3
            it >= 7 -> 2
            it >= 3 -> 1
            else -> 0
        }
    }

    fun saveSettings() {
        prefs.edit().putString("key", apiKey.trim()).putString("model", model.trim()).apply()
    }

    fun addMessage(m: Msg) {
        messages.add(m)
        val a = JSONArray()
        messages.takeLast(200).forEach { a.put(JSONObject().put("r", it.role).put("c", it.content)) }
        prefs.edit().putString(k("msgs"), a.toString()).apply()
    }

    fun clearChat() {
        messages.clear()
        prefs.edit().putString(k("msgs"), "[]").apply()
    }

    fun setTasks(list: List<Task>) {
        tasks.clear()
        tasks.addAll(list.sortedBy { it.time })
        val a = JSONArray()
        tasks.forEach { a.put(JSONObject().put("time", it.time).put("title", it.title).put("note", it.note).put("kind", it.kind).put("replaces", it.replaces)) }
        prefs.edit().putString(k("tasks"), a.toString()).apply()
        done.clear(); saveDone()
    }

    fun refreshDay() {
        if (flow.isNotEmpty() && doneDay != today()) { done.clear(); saveDone() }
    }

    fun toggleDone(id: Int) {
        refreshDay()
        if (id in done) done.remove(id) else done.add(id)
        saveDone()
    }

    fun markDone(id: Int) {
        refreshDay()
        if (id !in done) done.add(id)
        saveDone()
    }

    private fun saveDone() {
        doneDay = today()
        val a = JSONArray(); done.forEach { a.put(it) }
        prefs.edit().putLong(k("doneDay"), doneDay).putString(k("done"), a.toString()).apply()
    }

    fun startToday() {
        substances().forEach { chooseStart(it, today()) }
    }

    fun setIntake(label: String, value: String) {
        val i = intake.indexOfFirst { it.first == label }
        if (value.isBlank()) {
            if (i >= 0) { intake.removeAt(i) }
        } else if (i >= 0) {
            intake[i] = label to value
        } else {
            intake.add(label to value)
        }
        val a = JSONArray()
        intake.forEach { a.put(JSONArray().put(it.first).put(it.second)) }
        prefs.edit().putString(k("intake"), a.toString()).apply()
    }

    fun profileText(): String = intake.joinToString("\n") { "- ${it.first}: ${it.second}" }

    fun markOnboarded() {
        onboarded = true
        prefs.edit().putBoolean(k("onboarded"), true).apply()
    }

    fun redoOnboarding() {
        onboarded = false
        prefs.edit().putBoolean(k("onboarded"), false).apply()
    }

    fun chooseStartDay(day: Long) {
        startDay = day
        prefs.edit().putLong(k("start"), day).apply()
    }

    fun updatePersona(id: String) {
        persona = id
        prefs.edit().putString("persona", id).apply()
    }

    fun updateReduceMotion(on: Boolean) {
        reduceMotion = on
        prefs.edit().putBoolean("reduce_motion", on).apply()
    }

    fun setTipsSeen() {
        tipsSeen = true
        prefs.edit().putBoolean("tips_seen", true).apply()
    }

    fun scheduledIds(): List<Int> =
        (prefs.getString("sched", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

    fun setScheduledIds(ids: List<Int>) {
        prefs.edit().putString("sched", ids.joinToString(",")).apply()
    }
}
