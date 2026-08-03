package com.oj.drink

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object WaterRepository {
    private const val PREFS_NAME = "water_prefs"
    private const val KEY_WATER_ML = "water_ml"
    private const val KEY_DATE = "date"
    private const val KEY_HISTORY = "history"
    const val DAILY_GOAL_ML = 2000

    /** Days of history kept. Generous enough that a long streak is never truncated. */
    private const val HISTORY_LIMIT = 400

    /** Number of days shown in the chart — also the range you can back-fill into. */
    const val VISIBLE_DAYS = 7

    private data class Entry(val day: Long, val ml: Int, val fake: Boolean)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun today(): Long = LocalDate.now().toEpochDay()

    // ---------------------------------------------------------------- storage

    private fun readEntries(prefs: SharedPreferences): MutableList<Entry> {
        val json = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        return (0 until json.length()).mapTo(mutableListOf()) { i ->
            val obj = json.getJSONObject(i)
            Entry(obj.getLong("day"), obj.getInt("ml"), obj.optBoolean("fake", false))
        }
    }

    /** Sorts by day and keeps only the most recent [HISTORY_LIMIT] days before writing. */
    private fun writeEntries(prefs: SharedPreferences, entries: List<Entry>) {
        val sorted = entries.sortedBy { it.day }
        val kept = if (sorted.size > HISTORY_LIMIT) sorted.takeLast(HISTORY_LIMIT) else sorted
        val json = JSONArray()
        for (e in kept) {
            json.put(JSONObject().put("day", e.day).put("ml", e.ml).put("fake", e.fake))
        }
        prefs.edit().putString(KEY_HISTORY, json.toString()).apply()
    }

    private fun upsertHistory(prefs: SharedPreferences, day: Long, ml: Int, fake: Boolean = false) {
        val entries = readEntries(prefs)
        entries.removeAll { it.day == day }
        entries.add(Entry(day, ml, fake))
        writeEntries(prefs, entries)
    }

    private fun removeFromHistory(prefs: SharedPreferences, day: Long) {
        val entries = readEntries(prefs)
        if (entries.removeAll { it.day == day }) writeEntries(prefs, entries)
    }

    /**
     * Rolls today's counter over into history when the date has changed.
     * Called at the start of every read/write — there is no alarm or background job,
     * so any new public function touching [KEY_WATER_ML] must call this first.
     */
    private fun checkAndResetIfNeeded(prefs: SharedPreferences) {
        val today = today()
        val savedDay = prefs.getLong(KEY_DATE, -1L)
        if (savedDay == today) return
        if (savedDay != -1L) {
            // Days with nothing logged are left out of history entirely, so they count as
            // "no record" rather than dragging the rolling average down to zero.
            val ml = prefs.getInt(KEY_WATER_ML, 0)
            if (ml > 0) upsertHistory(prefs, savedDay, ml)
        }
        prefs.edit()
            .putInt(KEY_WATER_ML, 0)
            .putLong(KEY_DATE, today)
            .apply()
    }

    // ------------------------------------------------------------------ reads

    /** Every day that has a record, including today's in-progress total. */
    fun getAllDays(context: Context): Map<Long, Int> {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        val map = readEntries(p).associate { it.day to it.ml }.toMutableMap()
        val todayMl = p.getInt(KEY_WATER_ML, 0)
        if (todayMl > 0) map[today()] = todayMl else map.remove(today())
        return map
    }

    fun getCurrentMl(context: Context): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        return p.getInt(KEY_WATER_ML, 0)
    }

    fun getMlForDay(context: Context, day: Long): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        return if (day == today()) {
            p.getInt(KEY_WATER_ML, 0)
        } else {
            readEntries(p).firstOrNull { it.day == day }?.ml ?: 0
        }
    }

    /**
     * Streak recomputed from the data rather than tracked incrementally, so back-filling
     * a past day is picked up automatically. Today counts once the goal is met; until then
     * the streak is the run of days ending yesterday, so it stays alive during the day.
     */
    fun getStreak(context: Context): Int {
        val days = getAllDays(context)
        val today = today()
        var day = if ((days[today] ?: 0) >= DAILY_GOAL_ML) today else today - 1
        var streak = 0
        while ((days[day] ?: 0) >= DAILY_GOAL_ML) {
            streak++
            day--
        }
        return streak
    }

    // ----------------------------------------------------------------- writes

    fun addWater(context: Context, ml: Int): Int = addWaterForDay(context, today(), ml)

    /** Adds to any day; today goes into the live counter, past days are upserted into history. */
    fun addWaterForDay(context: Context, day: Long, ml: Int): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        val today = today()
        if (day > today) return getMlForDay(context, day)

        return if (day == today) {
            val newValue = (p.getInt(KEY_WATER_ML, 0) + ml).coerceAtLeast(0)
            p.edit().putInt(KEY_WATER_ML, newValue).apply()
            newValue
        } else {
            val current = readEntries(p).firstOrNull { it.day == day }?.ml ?: 0
            val newValue = (current + ml).coerceAtLeast(0)
            // Back-filled days are real data, so they survive "clear fake data".
            if (newValue > 0) upsertHistory(p, day, newValue) else removeFromHistory(p, day)
            newValue
        }
    }

    fun resetDay(context: Context, day: Long): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        if (day == today()) {
            p.edit().putInt(KEY_WATER_ML, 0).apply()
        } else {
            removeFromHistory(p, day)
        }
        return 0
    }

    // -------------------------------------------------------------- dev tools

    fun generateTestData(context: Context) {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        val today = today()
        val random = java.util.Random()
        val entries = readEntries(p)
        val realDays = entries.filter { !it.fake }.map { it.day }.toSet()
        for (daysAgo in 36 downTo 1) {
            val day = today - daysAgo
            if (day !in realDays) {
                entries.removeAll { it.day == day }
                entries.add(Entry(day, 1500 + random.nextInt(1751), fake = true))
            }
        }
        writeEntries(p, entries)
    }

    fun clearFakeData(context: Context) {
        val p = prefs(context)
        writeEntries(p, readEntries(p).filter { !it.fake })
    }

    fun clearAllData(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
