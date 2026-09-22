package com.example.flock.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.flock.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * OS-level task alerts. Alarms are scheduled a few days ahead (refreshed whenever the app is
 * opened / tasks change) and each notification carries Snooze + Complete actions. Notification
 * "Complete" writes to a SharedPreferences inbox that the app drains on next open (a
 * BroadcastReceiver must stay off the DB/network), so completion syncs to Room + the sheet then.
 */
object TaskNotify {
    const val CHANNEL_ID = "flockit_tasks"
    const val ACTION_FIRE = "com.example.flock.notify.FIRE"
    const val ACTION_COMPLETE = "com.example.flock.notify.COMPLETE"
    const val ACTION_SNOOZE = "com.example.flock.notify.SNOOZE"

    const val EX_SID = "sid"
    const val EX_FID = "fid"
    const val EX_TID = "tid"
    const val EX_DAY = "day"
    const val EX_LABEL = "label"
    const val EX_TIME = "time"

    private const val PREFS = "flockit_task_done_inbox"
    private const val SNOOZE_MIN = 15L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "Reminders for scheduled flock tasks"
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun reqCode(taskId: String, day: Int): Int = ("$taskId|$day").hashCode()

    private fun fireIntent(context: Context, action: String, sid: String, fid: String, tid: String, day: Int, label: String, time: String): Intent =
        Intent(context, TaskAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EX_SID, sid); putExtra(EX_FID, fid); putExtra(EX_TID, tid)
            putExtra(EX_DAY, day); putExtra(EX_LABEL, label); putExtra(EX_TIME, time)
        }

    private fun pi(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** (Re)schedules alarms for the next few days for a flock's alert-enabled, not-yet-done tasks. */
    fun scheduleUpcoming(context: Context, sid: String, fid: String, tasks: List<TaskEntity>, currentDay: Int, harvestAge: Int) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (offset in 0..3) {
            val day = currentDay + offset
            if (day > harvestAge) break
            val date = LocalDate.now().plusDays(offset.toLong())
            for (t in tasks) {
                if (!t.alertEnabled || !t.appliesOn(day, harvestAge) || t.isCompletedOn(day)) continue
                val parts = t.time.split(":")
                val hh = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val trigger = try {
                    date.atTime(hh.coerceIn(0, 23), mm.coerceIn(0, 59)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) { continue }
                if (trigger <= now) continue
                val code = reqCode(t.taskId, day)
                val p = pi(context, code, fireIntent(context, ACTION_FIRE, sid, fid, t.taskId, day, t.label, t.time))
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, p) } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    fun postNotification(context: Context, sid: String, fid: String, tid: String, day: Int, label: String, time: String) {
        ensureChannel(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val code = reqCode(tid, day)
        val completePI = pi(context, code + 1, fireIntent(context, ACTION_COMPLETE, sid, fid, tid, day, label, time))
        val snoozePI = pi(context, code + 2, fireIntent(context, ACTION_SNOOZE, sid, fid, tid, day, label, time))

        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(context)
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(label.ifBlank { "Farm task" })
            .setContentText("Due at $time · Day $day")
            .setAutoCancel(true)
            .addAction(@Suppress("DEPRECATION") Notification.Action.Builder(0, "Snooze ${SNOOZE_MIN}m", snoozePI).build())
            .addAction(@Suppress("DEPRECATION") Notification.Action.Builder(0, "Complete", completePI).build())
        if (Build.VERSION.SDK_INT < 26) @Suppress("DEPRECATION") builder.setPriority(Notification.PRIORITY_HIGH)
        try { mgr.notify(code, builder.build()) } catch (e: SecurityException) { /* no POST_NOTIFICATIONS */ }
    }

    fun markCompletedInInbox(context: Context, sid: String, tid: String, day: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putBoolean("$sid|$tid|$day", true).apply()
    }

    /** Drains the completion inbox (call from the app to sync to Room + sheet). */
    fun drainCompletedInbox(context: Context): List<Triple<String, String, Int>> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableListOf<Triple<String, String, Int>>()
        for ((k, v) in p.all) {
            if (v == true) {
                val parts = k.split("|")
                if (parts.size == 3) parts[2].toIntOrNull()?.let { out.add(Triple(parts[0], parts[1], it)) }
            }
        }
        if (out.isNotEmpty()) p.edit().clear().apply()
        return out
    }

    fun snooze(context: Context, sid: String, fid: String, tid: String, day: Int, label: String, time: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        val p = pi(context, reqCode(tid, day) + 3, fireIntent(context, ACTION_FIRE, sid, fid, tid, day, label, time))
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, p) } catch (e: Exception) { }
    }
}

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sid = intent.getStringExtra(TaskNotify.EX_SID) ?: return
        val fid = intent.getStringExtra(TaskNotify.EX_FID) ?: ""
        val tid = intent.getStringExtra(TaskNotify.EX_TID) ?: return
        val day = intent.getIntExtra(TaskNotify.EX_DAY, 0)
        val label = intent.getStringExtra(TaskNotify.EX_LABEL) ?: "Farm task"
        val time = intent.getStringExtra(TaskNotify.EX_TIME) ?: ""
        when (intent.action) {
            TaskNotify.ACTION_FIRE -> TaskNotify.postNotification(context, sid, fid, tid, day, label, time)
            TaskNotify.ACTION_COMPLETE -> {
                TaskNotify.markCompletedInInbox(context, sid, tid, day)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(("$tid|$day").hashCode())
            }
            TaskNotify.ACTION_SNOOZE -> {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(("$tid|$day").hashCode())
                TaskNotify.snooze(context, sid, fid, tid, day, label, time)
            }
        }
    }
}
