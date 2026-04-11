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
    private const val KEY_STREAK = "streak"
    private const val KEY_STREAK_COUNTED_TODAY = "streak_counted_today"
    const val DAILY_GOAL_ML = 2000

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun checkAndResetIfNeeded(prefs: SharedPreferences) {
        val today = LocalDate.now().toEpochDay()
        val savedDay = prefs.getLong(KEY_DATE, -1L)
        if (savedDay != today) {
            val editor = prefs.edit()
            if (savedDay != -1L) {
                val ml = prefs.getInt(KEY_WATER_ML, 0)
                saveToHistory(prefs, savedDay, ml)
                // Streak was already incremented intra-day when the goal was crossed.
                // At rollover we only need to reset it if the final intake missed the goal
                // or if more than one day passed (user skipped days).
                val daysPassed = today - savedDay
                if (daysPassed > 1L || ml < DAILY_GOAL_ML) {
                    editor.putInt(KEY_STREAK, 0)
                }
                // If ml >= DAILY_GOAL_ML and daysPassed == 1: streak is already correct.
            }
            editor
                .putInt(KEY_WATER_ML, 0)
                .putLong(KEY_DATE, today)
                .putBoolean(KEY_STREAK_COUNTED_TODAY, false)
                .apply()
        }
    }

    private fun saveToHistory(prefs: SharedPreferences, day: Long, ml: Int) {
        val json = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        // Rebuild without any existing entry for this day
        val updated = JSONArray()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            if (obj.getLong("day") != day) updated.put(obj)
        }
        updated.put(JSONObject().put("day", day).put("ml", ml).put("fake", false))
        // Keep only the most recent 37 entries
        val trimmed = JSONArray()
        val start = maxOf(0, updated.length() - 37)
        for (i in start until updated.length()) {
            trimmed.put(updated.getJSONObject(i))
        }
        prefs.edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun getHistory(context: Context): List<Pair<Long, Int>> {
        val p = prefs(context)
        val json = JSONArray(p.getString(KEY_HISTORY, "[]") ?: "[]")
        return (0 until json.length()).map { i ->
            val obj = json.getJSONObject(i)
            Pair(obj.getLong("day"), obj.getInt("ml"))
        }
    }

    fun getCurrentMl(context: Context): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        return p.getInt(KEY_WATER_ML, 0)
    }

    fun getStreak(context: Context): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        return p.getInt(KEY_STREAK, 0)
    }

    fun resetToday(context: Context): Int {
        val p = prefs(context)
        val today = LocalDate.now().toEpochDay()
        val editor = p.edit()
            .putInt(KEY_WATER_ML, 0)
            .putLong(KEY_DATE, today)
        // If the goal was reached today, decrement the streak since we're resetting below it
        if (p.getBoolean(KEY_STREAK_COUNTED_TODAY, false)) {
            val streak = p.getInt(KEY_STREAK, 0)
            editor
                .putInt(KEY_STREAK, maxOf(0, streak - 1))
                .putBoolean(KEY_STREAK_COUNTED_TODAY, false)
        }
        editor.apply()
        return 0
    }

    fun generateTestData(context: Context) {
        val p = prefs(context)
        val today = LocalDate.now().toEpochDay()
        val random = java.util.Random()
        // Build a set of days that already have real (fake=false) entries
        val existing = JSONArray(p.getString(KEY_HISTORY, "[]") ?: "[]")
        val realDays = mutableSetOf<Long>()
        for (i in 0 until existing.length()) {
            val obj = existing.getJSONObject(i)
            if (!obj.optBoolean("fake", false)) realDays.add(obj.getLong("day"))
        }
        // Keep all existing entries, add fake entries only for days without real data
        val updated = JSONArray()
        for (i in 0 until existing.length()) updated.put(existing.getJSONObject(i))
        for (daysAgo in 36 downTo 1) {
            val day = today - daysAgo
            if (day !in realDays) {
                val ml = 1500 + random.nextInt(1751) // 1500..3250
                updated.put(JSONObject().put("day", day).put("ml", ml).put("fake", true))
            }
        }
        p.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    fun clearFakeData(context: Context) {
        val p = prefs(context)
        val json = JSONArray(p.getString(KEY_HISTORY, "[]") ?: "[]")
        val kept = JSONArray()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            if (!obj.optBoolean("fake", false)) kept.put(obj)
        }
        p.edit().putString(KEY_HISTORY, kept.toString()).apply()
    }

    fun clearAllData(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun addWater(context: Context, ml: Int): Int {
        val p = prefs(context)
        checkAndResetIfNeeded(p)
        val current = p.getInt(KEY_WATER_ML, 0)
        val newValue = current + ml
        val today = LocalDate.now().toEpochDay()
        val editor = p.edit()
            .putInt(KEY_WATER_ML, newValue)
            .putLong(KEY_DATE, today)
        // Increment streak the first time the goal is crossed today
        if (current < DAILY_GOAL_ML && newValue >= DAILY_GOAL_ML &&
            !p.getBoolean(KEY_STREAK_COUNTED_TODAY, false)
        ) {
            editor
                .putInt(KEY_STREAK, p.getInt(KEY_STREAK, 0) + 1)
                .putBoolean(KEY_STREAK_COUNTED_TODAY, true)
        }
        editor.apply()
        return newValue
    }
}
