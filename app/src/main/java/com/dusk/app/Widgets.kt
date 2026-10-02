package com.dusk.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalTime

// ---------- Providers: one per widget size ----------

class DayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Day)
}
class ClearWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Clear)
}
class CravingWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Craving)
}
class StripWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Strip)
}
class IslandWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Island)
}
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx, mgr, ids, WKind.Today)
}

enum class WKind(val layout: Int, val cls: Class<out AppWidgetProvider>) {
    Day(R.layout.widget_day, DayWidget::class.java),
    Clear(R.layout.widget_clear, ClearWidget::class.java),
    Craving(R.layout.widget_craving, CravingWidget::class.java),
    Strip(R.layout.widget_strip, StripWidget::class.java),
    Island(R.layout.widget_island, IslandWidget::class.java),
    Today(R.layout.widget_today, TodayWidget::class.java),
}

/** Taps on widget buttons that don't open the app (checking off routine items), and refreshes. */
class WidgetActions : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        when (intent.action) {
            Widgets.ACTION_TOGGLE -> {
                val id = intent.getIntExtra("id", -1)
                if (id >= 0) Store.toggleDone(id)
                Widgets.updateAll(ctx)
            }
            else -> Widgets.updateAll(ctx)
        }
    }
}

object Widgets {
    const val ACTION_TOGGLE = "com.dusk.app.W_TOGGLE"
    const val ACTION_REFRESH = "com.dusk.app.W_REFRESH"
    private const val DAY_MS = 86_400_000L

    /** Redraws every Dusk widget on the home screen. Cheap when none are placed. */
    fun updateAll(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        var any = false
        WKind.entries.forEach { k ->
            val ids = mgr.getAppWidgetIds(ComponentName(app, k.cls))
            if (ids.isNotEmpty()) {
                any = true
                runCatching { update(app, mgr, ids, k, reschedule = false) }
            }
        }
        if (any) scheduleRollover(app)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, k: WKind, reschedule: Boolean = true) {
        Store.init(ctx)
        val rv = build(ctx, k)
        ids.forEach { mgr.updateAppWidget(it, rv) }
        if (reschedule) scheduleRollover(ctx)
    }

    private fun dark(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun openApp(ctx: Context, extra: String? = null, code: Int = 41_000): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra != null) i.putExtra("widget", extra)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The substance quit first (for "both", the earlier quit). */
    private fun mainSub(): String? =
        Store.substances().minByOrNull { Store.startMs(it).let { ms -> if (ms <= 0) Long.MAX_VALUE else ms } }

    /** Days as text plus a live hours:minutes:seconds chronometer, rolling over at the exact quit time. */
    private fun setClear(rv: RemoteViews, now: Long, prepText: String) {
        val sub = mainSub()
        val st = sub?.let { Store.startMs(it) } ?: -1L
        if (st in 1..now) {
            val elapsed = now - st
            val days = elapsed / DAY_MS
            val rem = elapsed % DAY_MS
            rv.setTextViewText(R.id.w_days, "${days}d")
            rv.setViewVisibility(R.id.w_days, if (days > 0) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.w_chrono, View.VISIBLE)
            rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() - rem, null, true)
        } else {
            rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false)
            rv.setViewVisibility(R.id.w_chrono, View.GONE)
            rv.setViewVisibility(R.id.w_days, View.VISIBLE)
            rv.setTextViewText(R.id.w_days, prepText)
        }
    }

    private fun prepText(): String {
        if (Store.flow.isEmpty() || !Store.onboarded) return "Open Dusk"
        val first = Store.firstStart()
        if (first < 0) return "Not started"
        val until = first - Store.today()
        return if (until == 1L) "Tomorrow" else "In ${until}d"
    }

    private fun dayText(): String {
        val first = Store.firstStart()
        return if (first in 0..Store.today()) "${Store.dayNumber()}" else "–"
    }

    private fun sunLevel(): Float {
        val total = Store.tasks.size
        val done = Store.done.count { id -> Store.tasks.any { it.id == id } }
        return if (total == 0) 0.75f else 1f - done.toFloat() / total
    }

    private fun scene(ctx: Context, w: Int, h: Int, horizon: Float = 0.68f): Bitmap =
        SceneArt.render(w, h, sunLevel(), Store.totalGulls(), Store.islandLevel(), dark(ctx), horizon)

    private fun kindBg(kind: String): Int = when (kind) {
        "body" -> R.drawable.w_row_body
        "mind" -> R.drawable.w_row_mind
        "food" -> R.drawable.w_row_food
        "sleep" -> R.drawable.w_row_sleep
        "social" -> R.drawable.w_row_social
        else -> R.drawable.w_row_other
    }

    private fun kindFg(ctx: Context, kind: String): Int = ctx.getColor(
        when (kind) {
            "body" -> R.color.kind_body_fg
            "mind" -> R.color.kind_mind_fg
            "food" -> R.color.kind_food_fg
            "sleep" -> R.color.kind_sleep_fg
            "social" -> R.color.kind_social_fg
            else -> R.color.kind_other_fg
        }
    )

    private fun build(ctx: Context, k: WKind): RemoteViews {
        val rv = RemoteViews(ctx.packageName, k.layout)
        val now = System.currentTimeMillis()
        val craving = openApp(ctx, "craving", 41_001)
        rv.setOnClickPendingIntent(R.id.w_root, openApp(ctx))

        when (k) {
            WKind.Day -> {
                rv.setImageViewBitmap(R.id.w_scene, scene(ctx, 220, 220, 0.72f))
                rv.setTextViewText(R.id.w_day, dayText())
            }
            WKind.Clear -> {
                mainSub()?.let { rv.setImageViewResource(R.id.w_icon, substanceIcon(it)) }
                setClear(rv, now, prepText())
            }
            WKind.Craving -> {
                rv.setImageViewBitmap(R.id.w_scene, scene(ctx, 420, 190))
                rv.setTextViewText(R.id.w_day, dayText())
                val g = Store.totalGulls()
                rv.setTextViewText(R.id.w_sub, "days · $g ${if (g == 1) "gull" else "gulls"}")
                rv.setOnClickPendingIntent(R.id.w_pill, craving)
            }
            WKind.Strip -> {
                setClear(rv, now, prepText())
                rv.setTextViewText(R.id.w_gulls, "${Store.totalGulls()}")
                rv.setTextViewText(R.id.w_next, Track.comingUp().firstOrNull() ?: "No known triggers left today")
            }
            WKind.Island -> {
                rv.setImageViewBitmap(R.id.w_scene, scene(ctx, 720, 330))
                val sub = mainSub()
                rv.setTextViewText(
                    R.id.w_label,
                    if (Store.flow == FLOW_BOTH) "Day" else "${flowName(sub ?: Store.flow)} · day"
                )
                rv.setTextViewText(R.id.w_day, dayText())
                setClear(rv, now, prepText())
                val g = Store.totalGulls()
                rv.setTextViewText(R.id.w_chip1, "$g ${if (g == 1) "gull" else "gulls"}")
                rv.setTextViewText(R.id.w_chip2, "${Store.sunsets()} sunsets")
                val rate = Track.passRate()
                rv.setViewVisibility(R.id.w_chip3, if (rate == null) View.GONE else View.VISIBLE)
                if (rate != null) rv.setTextViewText(R.id.w_chip3, "$rate% passed")
                rv.setOnClickPendingIntent(R.id.w_pill, craving)
            }
            WKind.Today -> {
                val total = Store.tasks.size
                val done = Store.done.count { id -> Store.tasks.any { it.id == id } }
                val first = Store.firstStart()
                rv.setTextViewText(
                    R.id.w_label,
                    if (first in 0..Store.today()) "Today · day ${Store.dayNumber()}" else "Today · ${prepText().lowercase()}"
                )
                rv.setTextViewText(R.id.w_done, if (total == 0) "No routine yet" else "$done of $total done")
                rv.setProgressBar(R.id.w_progress, 100, if (total == 0) 0 else done * 100 / total, false)
                setClear(rv, now, prepText())

                // The next three routine items from now, wrapping to the morning.
                val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
                fun mins(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
                val later = Store.tasks.filter { mins(it.time) >= nowMin - 30 }
                val rows = (later + Store.tasks).distinct().take(3)
                val rowIds = listOf(
                    listOf(R.id.w_row1, R.id.w_row1_icon, R.id.w_row1_time, R.id.w_row1_title, R.id.w_row1_check),
                    listOf(R.id.w_row2, R.id.w_row2_icon, R.id.w_row2_time, R.id.w_row2_title, R.id.w_row2_check),
                    listOf(R.id.w_row3, R.id.w_row3_icon, R.id.w_row3_time, R.id.w_row3_title, R.id.w_row3_check),
                )
                rowIds.forEachIndexed { i, (row, icon, time, title, check) ->
                    val t = rows.getOrNull(i)
                    if (t == null) {
                        rv.setViewVisibility(row, View.GONE)
                    } else {
                        val fg = kindFg(ctx, t.kind)
                        val isDone = t.id in Store.done
                        rv.setViewVisibility(row, View.VISIBLE)
                        rv.setInt(row, "setBackgroundResource", kindBg(t.kind))
                        rv.setImageViewResource(icon, kindIcon(t.kind))
                        rv.setInt(icon, "setColorFilter", fg)
                        rv.setTextViewText(time, t.time)
                        rv.setTextColor(time, fg)
                        rv.setTextViewText(title, t.title)
                        rv.setTextColor(title, fg)
                        rv.setImageViewResource(check, if (isDone) R.drawable.ic_t_circle_check else R.drawable.ic_ring)
                        rv.setInt(check, "setColorFilter", fg)
                        rv.setContentDescription(check, if (isDone) "Mark ${t.title} not done" else "Mark ${t.title} done")
                        val toggle = PendingIntent.getBroadcast(
                            ctx, 40_000 + t.id,
                            Intent(ctx, WidgetActions::class.java).setAction(ACTION_TOGGLE).putExtra("id", t.id),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        rv.setOnClickPendingIntent(check, toggle)
                    }
                }
                rv.setViewVisibility(R.id.w_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
                rv.setTextViewText(R.id.w_next, Track.comingUp().firstOrNull() ?: "No known triggers left today")
                rv.setOnClickPendingIntent(R.id.w_pill, craving)
            }
        }
        return rv
    }

    /** Refresh at the next whole day since quitting (so the day count rolls over) and at midnight. */
    private fun scheduleRollover(ctx: Context) {
        val now = System.currentTimeMillis()
        val st = mainSub()?.let { Store.startMs(it) } ?: -1L
        val nextQuitDay = if (st in 1..now) st + ((now - st) / DAY_MS + 1) * DAY_MS else Long.MAX_VALUE
        val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val at = minOf(nextQuitDay, midnight) + 1_000
        val pi = PendingIntent.getBroadcast(
            ctx, 41_100,
            Intent(ctx, WidgetActions::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }
}

/** Draws the island at sunset into a bitmap, matching the in-app scene. */
object SceneArt {
    private fun c(v: Long) = v.toInt()

    fun render(w: Int, h: Int, sunLevel: Float, gulls: Int, island: Int, dark: Boolean, horizonFrac: Float = 0.68f): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val sky = if (dark) intArrayOf(c(0xFF14223A), c(0xFF2A3550), c(0xFFC9735A)) else intArrayOf(c(0xFFFFD2B0), c(0xFFF6B4AE), c(0xFFFF9F80))
        val sun = if (dark) c(0xFFFFC48A) else c(0xFFFFE29A)
        val sea = if (dark) c(0xFF17344F) else c(0xFF4F9AA3)
        val wave = if (dark) c(0xFF3E6A85) else c(0xFF9FD0D4)
        val sand = if (dark) c(0xFF8C6E57) else c(0xFFE9C9A0)
        val ink = if (dark) c(0xFFF4EBE1) else c(0xFF1F3A4D)
        val palm = if (dark) c(0xFF5E9C7E) else c(0xFF4E9A78)
        val trunk = c(0xFF6B5A3E)
        val light = c(0xFFFFF6EC)

        val s = h / 150f
        val horizon = h * horizonFrac
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, horizon, sky, null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), horizon, p)
        p.shader = null

        val r = h * 0.17f
        val sunY = horizon - sunLevel.coerceIn(0f, 1f) * (horizon - r * 1.4f)
        p.color = sun; p.alpha = 90
        cv.drawCircle(w / 2f, sunY, r * 1.5f, p)
        p.alpha = 255
        cv.drawCircle(w / 2f, sunY, r, p)

        p.color = sea
        cv.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.color = wave; p.style = Paint.Style.STROKE; p.strokeWidth = 3 * s; p.strokeCap = Paint.Cap.ROUND
        listOf(Triple(0.16f, 0.38f, 0.22f), Triple(0.52f, 0.86f, 0.45f), Triple(0.30f, 0.50f, 0.70f)).forEach { (x1, x2, yf) ->
            val y = horizon + (h - horizon) * yf
            cv.drawLine(w * x1, y, w * x2, y, p)
        }

        // Island
        p.style = Paint.Style.FILL; p.color = sand
        val ix = w * 0.80f
        val iw = w * 0.17f
        cv.drawPath(Path().apply {
            moveTo(ix - iw, horizon + 1); quadTo(ix, horizon - h * 0.11f, ix + iw, horizon + 1); close()
        }, p)
        val ground = horizon - h * 0.045f

        if (island >= 1) { // palm
            val x = ix + iw * 0.15f
            val tx = x + 4 * s; val ty = ground - 26 * s
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * s; p.color = trunk
            cv.drawLine(x, ground, tx, ty, p)
            p.color = palm
            val f = 13 * s
            listOf(-1f to 0.35f, 1f to 0.35f, -0.8f to -0.45f, 0.8f to -0.45f).forEach { (dx, dy) ->
                cv.drawPath(Path().apply { moveTo(tx, ty); quadTo(tx + dx * f * 0.6f, ty - f * 0.6f, tx + dx * f, ty + dy * f) }, p)
            }
            p.style = Paint.Style.FILL
        }
        if (island >= 2) { // hut
            val x = ix - iw * 0.45f; val gy = ground + 2 * s
            val bw = 16 * s; val bh = 10 * s
            p.color = c(0xFFC98A5A); cv.drawRect(x - bw / 2, gy - bh, x + bw / 2, gy, p)
            p.color = trunk
            cv.drawPath(Path().apply { moveTo(x - bw * 0.7f, gy - bh); lineTo(x, gy - bh - 9 * s); lineTo(x + bw * 0.7f, gy - bh); close() }, p)
        }
        if (island >= 3) { // boat
            val x = w * 0.22f; val y = horizon + (h - horizon) * 0.32f; val hw = 15 * s
            p.color = trunk
            cv.drawPath(Path().apply { moveTo(x - hw, y); lineTo(x + hw, y); lineTo(x + hw * 0.7f, y + 6 * s); lineTo(x - hw * 0.7f, y + 6 * s); close() }, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2 * s
            cv.drawLine(x, y, x, y - 20 * s, p)
            p.style = Paint.Style.FILL; p.color = light
            cv.drawPath(Path().apply { moveTo(x + s, y - 20 * s); lineTo(x + 12 * s, y - 3 * s); lineTo(x + s, y - 3 * s); close() }, p)
        }
        if (island >= 4) { // lighthouse
            val x = ix + iw * 0.62f; val gy = horizon - s; val th = 30 * s
            p.color = light
            cv.drawPath(Path().apply { moveTo(x - 5 * s, gy); lineTo(x + 5 * s, gy); lineTo(x + 3 * s, gy - th); lineTo(x - 3 * s, gy - th); close() }, p)
            p.color = c(0xFFF0765A); cv.drawRect(x - 4.2f * s, gy - th * 0.45f, x + 4.2f * s, gy - th * 0.45f + 4 * s, p)
            p.color = sun; cv.drawCircle(x, gy - th - 3 * s, 4 * s, p)
        }

        // Gulls, one per craving ridden out
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * s; p.color = ink; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until gulls.coerceIn(0, 12)) {
            val x = ((i * 0.37f + 0.11f) % 1f) * w
            val y = horizon * (0.12f + ((i * 0.53f) % 0.45f))
            val g = (5 + (i % 3) * 2) * s
            cv.drawPath(Path().apply {
                moveTo(x - g, y); quadTo(x - g / 2, y - g * 0.7f, x, y); quadTo(x + g / 2, y - g * 0.7f, x + g, y)
            }, p)
        }
        return bmp
    }
}
