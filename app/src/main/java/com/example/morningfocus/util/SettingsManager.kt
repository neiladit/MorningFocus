package com.example.morningfocus.util

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME

    companion object {
        private const val PREFS_NAME = "MorningFocusSettings"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_WINDOW_SET = "window_set"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_APP_BLOCKING_ENABLED = "app_blocking_enabled"
    }

    fun saveStartTime(time: LocalTime) {
        prefs.edit().putString(KEY_START_TIME, time.format(timeFormatter)).apply()
    }

    fun saveEndTime(time: LocalTime) {
        prefs.edit().putString(KEY_END_TIME, time.format(timeFormatter)).apply()
    }

    fun saveWindowSet(isSet: Boolean) {
        prefs.edit().putBoolean(KEY_WINDOW_SET, isSet).apply()
    }

    fun saveBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, enabled).apply()
    }

    fun saveAppBlockingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_BLOCKING_ENABLED, enabled).apply()
    }

    fun getStartTime(): LocalTime {
        val timeStr = prefs.getString(KEY_START_TIME, null)
        return if (timeStr != null) {
            LocalTime.parse(timeStr, timeFormatter)
        } else {
            LocalTime.of(9, 0) // Default start time
        }
    }

    fun getEndTime(): LocalTime {
        val timeStr = prefs.getString(KEY_END_TIME, null)
        return if (timeStr != null) {
            LocalTime.parse(timeStr, timeFormatter)
        } else {
            LocalTime.of(10, 0) // Default end time
        }
    }

    fun isWindowSet(): Boolean {
        return prefs.getBoolean(KEY_WINDOW_SET, false)
    }

    fun isBlockingEnabled(): Boolean {
        return prefs.getBoolean(KEY_BLOCKING_ENABLED, false)
    }

    fun isAppBlockingEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_BLOCKING_ENABLED, false)
    }
} 