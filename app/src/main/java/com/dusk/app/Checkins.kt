package com.dusk.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Optional check-ins. Dusk asks a short question ahead of the hard moments.
 * Off unless the person chooses them; frequency and duration are theirs to pick.
 */
object Checkins {
    const val CHANNEL = "checkins"
    const val ACTION_CHECKIN = "com.dusk.app.CHECKIN"
    const val ACTION_DISMISS = "com.dusk.app.CHECKIN_DISMISS"
    const val NOTIF_ID = 30_100
    private const val BASE = 30_000

    fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Check-ins from Dusk", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Optional questions from Dusk ahead of your hard moments."
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun mins(t: String): Int = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
    private fun fmt(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    /**
     * Check-in times. When the routine has trigger moments, check-ins land about
     * 45 minutes before them so there's time to plan. Otherwise, sensible defaults.
     */
    fun times(): List<String> {
        val n = Store.checkinFreq.coerceIn(0, 3)
        if (n == 0) return emptyList()
        val defaults = when (n) {
            1 -> listOf("18:30")
            2 -> listOf("12:00", "18:30")
            else -> listOf("09:30", "14:00", "19:30")
        }
        val candidates = Store.tasks.filter { it.replaces.isNotBlank() }
            .map { mins(it.time) - 45 }
            .filter { it in 8 * 60..21 * 60 }
            .distinct().sorted()
        if (candidates.size < n) return defaults
        val picked = if (n == 1) listOf(candidates.last())
        else (0 until n).map { candidates[it * (candidates.size - 1) / (n - 1)] }
        val result = picked.distinct().map(::fmt)
        return if (result.size < n) defaults else result
    }

    /** Check-ins run from day 1 for two weeks, or ongoing if the person chose that. */
    fun active(): Boolean {
        val d = Store.dayNumber()
        return Store.checkinFreq > 0 && Store.firstStart() >= 0 && d >= 1 && (Store.checkinOngoing || d <= 14)
    }

    /** What Dusk asks. Planning ahead when a trigger is coming, otherwise fitted to the day. */
    fun question(time: String): String {
        val m = mins(time)
        val next = Store.tasks.filter { it.replaces.isNotBlank() }.firstOrNull { mins(it.time) - m in 0..120 }
        if (next != null) {
            return "${next.replaces.replaceFirstChar { it.uppercase() }} is coming up around ${next.time}. What's your plan for it?"
        }
        val d = Store.dayNumber()
        val bank = when {
            d <= 1 -> listOf("Day one. How loud is it so far?", "First day. What's been the hardest moment yet?")
            d <= 3 -> listOf(
                "These are usually the loudest days. What's hitting hardest right now?",
                "Sleep, temper, appetite. Which one's worst today?"
            )
            d <= 7 -> listOf(
                "Easing days are when people get careless. Anything tempting you to test it?",
                "Most slips happen this week. Where's the risk today?"
            )
            d <= 14 -> listOf(
                "Quiet days are the risky ones. Where has \"just one\" been showing up?",
                "What's the plan for tonight?"
            )
            else -> listOf("Any old habits knocking lately?", "How's it going, honestly?")
        }
        return bank[(d + m / 60) % bank.size]
    }

    private fun alarm(ctx: Context, i: Int, time: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, BASE + i,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_CHECKIN).putExtra("time", time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Cancels all check-in alarms and sets the current ones (none if switched off). */
    fun schedule(ctx: Context) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        (0 until 3).forEach { am.cancel(alarm(ctx, it, "")) }
        times().forEachIndexed { i, t ->
            val at = Reminders.nextTrigger(t)
            val pi = alarm(ctx, i, t)
            if (Reminders.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun show(ctx: Context, time: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val q = question(time)
        val p = personaById(Store.persona)
        fun open(mode: String, code: Int): PendingIntent = PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("checkin", mode).putExtra("question", q),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            ctx, 30_200,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(p.name)
            .setContentText(q)
            .setStyle(Notification.BigTextStyle().bigText(q))
            .setContentIntent(open("talk", 30_300))
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null as Icon?, "Check in", open("quick", 30_301)).build())
            .addAction(Notification.Action.Builder(null as Icon?, "Talk", open("talk", 30_302)).build())
            .addAction(Notification.Action.Builder(null as Icon?, "Not now", dismiss).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}
