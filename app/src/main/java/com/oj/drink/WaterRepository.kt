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
    const val DAILY_GOAL_ML = 3000

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun checkAndResetIfNeeded(prefs: SharedPreferences) {
        val today = LocalDate.now().toEpochDay()
        val savedDay = prefs.getLong(KEY_DATE, -1L)
        if (savedDay != today) {
            if (savedDay != -1L) {
                val ml = prefs.getInt(KEY_WATER_ML, 0)
                saveToHistory(prefs, savedDay, ml)
            }
            prefs.edit()
                .putInt(KEY_WATER_ML, 0)
                .putLong(KEY_DATE, today)
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

    fun resetToday(context: Context): Int {
        val p = prefs(context)
        val today = LocalDate.now().toEpochDay()
        p.edit()
            .putInt(KEY_WATER_ML, 0)
            .putLong(KEY_DATE, today)
            .apply()
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
        p.edit()
            .putInt(KEY_WATER_ML, newValue)
            .putLong(KEY_DATE, today)
            .apply()
        return newValue
    }
}
