package com.dusk.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "routine"
    const val ACTION_REMIND = "com.dusk.app.REMIND"
    const val ACTION_DONE = "com.dusk.app.DONE"
    const val ACTION_PREP = "com.dusk.app.PREP"
    const val PREP_ID = 20_000

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Routine reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun alarmIntent(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun prepIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, PREP_ID,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_PREP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** One nudge at 18:00 the evening before day 1, when day 1 is still ahead. */
    fun schedulePrep(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = prepIntent(ctx)
        am.cancel(pi)
        val first = Store.firstStart()
        if (first <= Store.today()) return
        val t = LocalDate.ofEpochDay(first - 1).atTime(18, 0)
        if (!t.isAfter(LocalDateTime.now())) return
        val at = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun nextTrigger(time: String): Long {
        val (h, m) = time.split(":").map { it.toInt() }
        var t = LocalDate.now().atTime(h, m)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, task: Task) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = nextTrigger(task.time)
        val pi = alarmIntent(ctx, task.id)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Cancels every old reminder and schedules the current routine. */
    fun rescheduleAll(ctx: Context) {
        Store.init(ctx)
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        Store.scheduledIds().forEach { am.cancel(alarmIntent(ctx, it)) }
        Store.tasks.forEach { schedule(ctx, it) }
        Store.setScheduledIds(Store.tasks.map { it.id })
        schedulePrep(ctx)
    }

    fun show(ctx: Context, id: Int, title: String, text: String, withDone: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (withDone) {
            val done = PendingIntent.getBroadcast(
                ctx, 10_000 + id,
                Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DONE).putExtra("id", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(Notification.Action.Builder(null as Icon?, "Done", done).build())
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        if (intent.action == Reminders.ACTION_PREP) {
            Reminders.show(
                ctx, Reminders.PREP_ID, "Day 1 is tomorrow",
                "Tonight, clear out anything you'd reach for. Tomorrow will be hard. You'll be ready.", withDone = false
            )
            return
        }
        val id = intent.getIntExtra("id", -1)
        val task = Store.tasks.firstOrNull { it.id == id } ?: return
        when (intent.action) {
            Reminders.ACTION_DONE -> {
                Store.markDone(id)
                ctx.getSystemService(NotificationManager::class.java).cancel(id)
            }
            else -> {
                // Before day 1 the routine stays quiet; it starts on the quit day.
                val preparing = Store.firstStart() >= 0 && Store.today() < Store.firstStart()
                if (!preparing) {
                    Reminders.show(ctx, id, task.title, task.note.ifBlank { "It's ${task.time}. Time for this one." })
                }
                Reminders.schedule(ctx, task) // same time tomorrow
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.rescheduleAll(ctx)
    }
}
