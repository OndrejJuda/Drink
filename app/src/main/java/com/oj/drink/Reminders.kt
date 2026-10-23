package com.oj.drink

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "Are you behind?" reminders. There is still no background job for the data itself — this
 * is a chain of inexact alarms, each one scheduling the next slot after it runs. Reboots,
 * clock changes and app updates wipe alarms, so [ReminderReceiver] restarts the chain then,
 * and [MainActivity] does too on every launch.
 */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1
    private const val EXTRA_SLOT = "slot"

    /** Notify only when further behind than this. */
    const val TOLERANCE_ML = 100

    /** A regular check is skipped if something was logged this recently. */
    private const val RECENT_ADD_MINUTES = 45L

    private const val WINDOW_MINUTES = 10L

    /** An alarm delayed by Doze longer than this is stale and only reschedules. */
    private const val STALE_AFTER_MINUTES = 60L

    /**
     * Expected cumulative intake through the day, linearly interpolated between points.
     * 1.5 L by 13:00 is the main milestone; the last point is the end of the drinking day,
     * half an hour before bed.
     */
    private val PACE = listOf(
        LocalTime.of(6, 0) to 0,
        LocalTime.of(13, 0) to 1500,
        LocalTime.of(21, 0) to WaterRepository.DAILY_GOAL_ML,
    )

    private val MILESTONE = LocalTime.of(13, 0)
    private val STREAK_CHECK = LocalTime.of(20, 30)

    /** Hourly while the pace is steep, every two hours after the milestone. */
    private val SLOTS = listOf(
        LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0),
        LocalTime.of(11, 0), LocalTime.of(12, 0), MILESTONE,
        LocalTime.of(15, 0), LocalTime.of(17, 0), LocalTime.of(19, 0),
        STREAK_CHECK,
    )

    fun expectedMl(time: LocalTime): Int {
        if (time <= PACE.first().first) return PACE.first().second
        for ((a, b) in PACE.zipWithNext()) {
            if (time <= b.first) {
                val span = b.first.toSecondOfDay() - a.first.toSecondOfDay()
                val done = time.toSecondOfDay() - a.first.toSecondOfDay()
                return a.second + (b.second - a.second) * done / span
            }
        }
        return PACE.last().second
    }

    // ------------------------------------------------------------- scheduling

    fun scheduleNext(context: Context) {
        val now = LocalDateTime.now()
        val next = SLOTS.firstOrNull { it > now.toLocalTime() }
            ?.let { now.toLocalDate().atTime(it) }
            ?: now.toLocalDate().plusDays(1).atTime(SLOTS.first())

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_CHECK
            putExtra(EXTRA_SLOT, next.toLocalTime().toSecondOfDay())
        }
        val pi = PendingIntent.getBroadcast(
            context, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Not exact on purpose: exact alarms need SCHEDULE_EXACT_ALARM. A plain inexact alarm
        // isn't good enough either — Samsung stretched its window past an hour, which would
        // run into the next slot — so it gets an explicit 10-minute window.
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MINUTES * 60_000, pi)
    }

    // ------------------------------------------------------------------ check

    fun runCheck(context: Context, slotSecondOfDay: Int) {
        val slot = LocalTime.ofSecondOfDay(slotSecondOfDay.toLong())
        val now = LocalTime.now()
        val late = java.time.Duration.between(slot, now).toMinutes()
        if (late < -5 || late > STALE_AFTER_MINUTES) return

        val current = WaterRepository.getCurrentMl(context)
        val goal = WaterRepository.DAILY_GOAL_ML
        if (current >= goal) {
            cancel(context)
            return
        }

        if (slot == STREAK_CHECK) {
            // Today isn't met yet, so this is the run ending yesterday — the one at stake.
            val streak = WaterRepository.getStreak(context)
            if (streak > 0) {
                show(
                    context,
                    "Série ${streak} ${days(streak)} je v ohrožení",
                    "Do cíle chybí ${ml(goal - current)}."
                )
                return
            }
        }

        val expected = expectedMl(now)
        val deficit = expected - current
        if (deficit <= TOLERANCE_ML) return

        if (slot == MILESTONE) {
            show(
                context,
                "Do 13:00 mělo být ${liters(expected)}",
                "Máš ${liters(current)}, chybí ${ml(deficit)}."
            )
            return
        }

        val sinceAdd = System.currentTimeMillis() - WaterRepository.getLastAddAt(context)
        if (sinceAdd < RECENT_ADD_MINUTES * 60_000) return

        show(
            context,
            "Jsi pozadu o ${ml(deficit)}",
            "Teď by mělo být ${liters(expected)}, máš ${liters(current)}. " +
                "Do cíle chybí ${liters(goal - current)}."
        )
    }

    /** Called after any add, so a reminder you've already acted on doesn't hang around. */
    fun onWaterAdded(context: Context) {
        val current = WaterRepository.getCurrentMl(context)
        if (current >= WaterRepository.DAILY_GOAL_ML ||
            expectedMl(LocalTime.now()) - current <= TOLERANCE_ML
        ) {
            cancel(context)
        }
    }

    // ----------------------------------------------------------- notification

    private fun show(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Připomínky pití", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val open = PendingIntent.getActivity(
            context, 11,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val add = PendingIntent.getBroadcast(
            context, 12,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_ADD_250
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "+250 ml", add)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    // --------------------------------------------------------------- formatting

    private val CZ = Locale.forLanguageTag("cs")

    private fun liters(ml: Int) = String.format(CZ, "%.1f L", ml / 1000f)

    /** Rounded to tens so the interpolated pace doesn't produce numbers like 643 ml. */
    private fun ml(ml: Int) = "${(ml / 10f).roundToInt() * 10} ml"

    private fun days(n: Int) = when {
        n == 1 -> "den"
        n in 2..4 -> "dny"
        else -> "dní"
    }

    internal fun slotOf(intent: Intent) = intent.getIntExtra(EXTRA_SLOT, -1)
}

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.oj.drink.REMINDER_CHECK"
        const val ACTION_ADD_250 = "com.oj.drink.REMINDER_ADD_250"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CHECK -> {
                val slot = Reminders.slotOf(intent)
                if (slot >= 0) Reminders.runCheck(context, slot)
                Reminders.scheduleNext(context)
            }
            ACTION_ADD_250 -> {
                WaterRepository.addWater(context, 250)
                WaterWidget.requestUpdate(context)
                Reminders.cancel(context)
            }
            // Boot, clock/timezone change, app update: alarms are gone, restart the chain.
            else -> Reminders.scheduleNext(context)
        }
    }
}
