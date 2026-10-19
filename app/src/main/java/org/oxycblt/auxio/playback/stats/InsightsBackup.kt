/*
 * Copyright (c) 2026 Auxio Project
 * InsightsBackup.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.playback.stats

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Versioned, portable snapshot of local listening statistics.
 *
 * The snapshot deliberately contains the complete listening_stats preference set so that
 * track counts, listening time, streaks, active/completed sessions, and recent history move
 * together. Restore rejects older snapshots rather than replacing newer local data.
 */
internal object InsightsBackup {
    private const val PREFERENCES_NAME = "listening_stats"
    private const val FORMAT_VERSION = 1
    private const val KEY_DATA_UPDATED_AT = "backup_data_updated_at"

    data class RestoreResult(
        val restored: Boolean,
        val localUpdatedAt: Long,
        val backupUpdatedAt: Long,
    )

    fun export(context: Context, now: Long = System.currentTimeMillis()): String {
        val preferences = preferences(context)
        val values = JSONObject()
        preferences.all.forEach { (key, value) ->
            when (value) {
                is String -> values.put(key, JSONObject().put("type", "string").put("value", value))
                is Boolean -> values.put(key, JSONObject().put("type", "boolean").put("value", value))
                is Int -> values.put(key, JSONObject().put("type", "int").put("value", value))
                is Long -> values.put(key, JSONObject().put("type", "long").put("value", value))
                is Float -> values.put(key, JSONObject().put("type", "float").put("value", value.toDouble()))
            }
        }
        val updatedAt = latestLocalUpdate(preferences)
        return JSONObject()
            .put("format", "auxio-insights-backup")
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", now)
            .put("dataUpdatedAt", updatedAt)
            .put("data", values)
            .toString(2)
    }

    fun restore(context: Context, content: String): RestoreResult {
        val root = JSONObject(content)
        require(root.optString("format") == "auxio-insights-backup") {
            "This file is not an Auxio Insights backup."
        }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) {
            "This Insights backup version is not supported by this app."
        }
        val values = root.optJSONObject("data")
            ?: throw IllegalArgumentException("The backup does not contain Insights data.")
        val incomingUpdatedAt = root.optLong("dataUpdatedAt", 0L)
        val preferences = preferences(context)
        val localUpdatedAt = latestLocalUpdate(preferences)
        require(incomingUpdatedAt >= localUpdatedAt) {
            "A newer local Insights history exists. Restore was stopped to protect it."
        }

        val editor = preferences.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = values.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "string" -> editor.putString(key, entry.optString("value"))
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
            }
        }
        editor.putLong(KEY_DATA_UPDATED_AT, maxOf(incomingUpdatedAt, root.optLong("exportedAt", 0L)))
        check(editor.commit()) { "Could not save restored Insights data." }
        return RestoreResult(true, localUpdatedAt, incomingUpdatedAt)
    }

    private fun latestLocalUpdate(preferences: SharedPreferences): Long {
        val trackLastPlayed = preferences.all
            .filterKeys { it.startsWith("track_") && it.endsWith("_last") }
            .values
            .filterIsInstance<Long>()
            .maxOrNull() ?: 0L
        return maxOf(
            preferences.getLong(KEY_DATA_UPDATED_AT, 0L),
            preferences.getLong("last_tick_ms", 0L),
            trackLastPlayed,
        )
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
