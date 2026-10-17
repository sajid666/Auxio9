/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackStatsTracker.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.playback.stats

import android.content.Context
import androidx.media3.common.MediaItem
import java.time.LocalDate
import org.json.JSONObject

/** A locally tracked song summary. */
data class TrackedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val plays: Long,
    val listeningMs: Long,
    val lastPlayedMs: Long,
)

/** A ranked artist or album. */
data class TrackedGroup(val name: String, val plays: Long, val listeningMs: Long)

/** A recent play event. */
data class ListeningEvent(
    val title: String,
    val artist: String,
    val album: String,
    val timestampMs: Long,
)

/** Local, install-forward listening statistics stored on this device. */
class PlaybackStatsTracker(
    context: Context,
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
    private val currentEpochDay: () -> Long = { LocalDate.now().toEpochDay() },
) {
    private val preferences =
        context.applicationContext.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)

    fun recordSecond(item: MediaItem?) {
        item ?: return
        val id = trackId(item)
        if (id.isBlank()) return
        val now = currentTimeMs()
        val today = currentEpochDay()
        val lastDay = preferences.getLong(KEY_LAST_LISTEN_DAY, -1L)
        val editor = preferences.edit()
        editor.putLong(KEY_TOTAL_MS, preferences.getLong(KEY_TOTAL_MS, 0L) + 1_000L)
        editor.putLong(key(id, "time"), preferences.getLong(key(id, "time"), 0L) + 1_000L)

        if (today != lastDay) {
            val yesterday = today - 1L
            val streak = preferences.getLong(KEY_STREAK_DAYS, 0L)
            editor.putLong(KEY_STREAK_DAYS, if (lastDay == yesterday) streak + 1L else 1L)
            editor.putLong(KEY_LAST_LISTEN_DAY, today)
        }

        val previousTick = preferences.getLong(KEY_LAST_TICK_MS, 0L)
        var sessionStart = preferences.getLong(KEY_SESSION_START_MS, 0L)
        var sessionDuration = preferences.getLong(KEY_SESSION_DURATION_MS, 0L)
        var completedDuration = preferences.getLong(KEY_COMPLETED_SESSION_MS, 0L)
        var completedCount = preferences.getLong(KEY_COMPLETED_SESSION_COUNT, 0L)
        if (
            previousTick > 0L &&
                now > previousTick &&
                now - previousTick > SESSION_GAP_MS &&
                sessionStart > 0L
        ) {
            completedDuration += sessionDuration
            completedCount++
            sessionStart = 0L
            sessionDuration = 0L
        }
        if (sessionStart == 0L) sessionStart = now
        sessionDuration += 1_000L
        editor.putLong(KEY_LAST_TICK_MS, now)
        editor.putLong(KEY_SESSION_START_MS, sessionStart)
        editor.putLong(KEY_SESSION_DURATION_MS, sessionDuration)
        editor.putLong(KEY_COMPLETED_SESSION_MS, completedDuration)
        editor.putLong(KEY_COMPLETED_SESSION_COUNT, completedCount)
        editor.apply()
    }

    /** Return zero when the last listening day is too old to be part of the current streak. */
    fun listeningStreakDays(): Long {
        val today = currentEpochDay()
        val lastListenDay = preferences.getLong(KEY_LAST_LISTEN_DAY, -1L)
        if (lastListenDay != today && lastListenDay != today - 1L) {
            return 0L
        }
        return preferences.getLong(KEY_STREAK_DAYS, 0L)
    }

    fun averageSessionMs(): Long {
        finishSessionIfIdle(currentTimeMs())

        val completedCount = preferences.getLong(KEY_COMPLETED_SESSION_COUNT, 0L)
        val completedDuration = preferences.getLong(KEY_COMPLETED_SESSION_MS, 0L)
        val activeDuration = preferences.getLong(KEY_SESSION_DURATION_MS, 0L)
        val active = if (preferences.getLong(KEY_SESSION_START_MS, 0L) > 0L) 1L else 0L
        val count = completedCount + active
        return if (count == 0L) 0L else (completedDuration + activeDuration) / count
    }

    /** Count a play when a track starts, rather than on every progress tick. */
    fun recordPlay(item: MediaItem?) {
        item ?: return
        val metadata = item.mediaMetadata
        val id = trackId(item)
        if (id.isBlank()) return
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        val album = metadata.albumTitle?.toString().orEmpty()
        val now = currentTimeMs()
        val editor =
            preferences.edit()
                .putLong(key(id, "plays"), preferences.getLong(key(id, "plays"), 0L) + 1L)
                .putString(key(id, "title"), title)
                .putString(key(id, "artist"), artist)
                .putString(key(id, "album"), album)
                .putLong(key(id, "last"), now)
        for (index in HISTORY_LIMIT - 1 downTo 1) {
            preferences.getString(historyKey(index - 1), null)?.let {
                editor.putString(historyKey(index), it)
            }
        }
        editor.putString(
            historyKey(0),
            JSONObject()
                .put("title", title)
                .put("artist", artist)
                .put("album", album)
                .put("timestamp", now)
                .toString(),
        )
        editor.apply()
    }

    fun totalListeningMs(): Long = preferences.getLong(KEY_TOTAL_MS, 0L)

    fun mostPlayed(limit: Int = 10): List<TrackedSong> {
        if (limit <= 0) return emptyList()
        val ids =
            preferences.all.keys
                .filter { it.startsWith(TRACK_PREFIX) && it.endsWith("_plays") }
                .map { it.removePrefix(TRACK_PREFIX).removeSuffix("_plays") }
        return ids.mapNotNull { id ->
            val plays = preferences.getLong(trackKey(id, "plays"), 0L)
            if (plays <= 0L) {
                null
            } else {
                TrackedSong(
                    id = id,
                    title = preferences.getString(trackKey(id, "title"), null).orEmpty(),
                    artist = preferences.getString(trackKey(id, "artist"), null).orEmpty(),
                    album = preferences.getString(trackKey(id, "album"), null).orEmpty(),
                    plays = plays,
                    listeningMs = preferences.getLong(trackKey(id, "time"), 0L),
                    lastPlayedMs = preferences.getLong(trackKey(id, "last"), 0L),
                )
            }
        }.sortedWith(
            compareByDescending<TrackedSong> { it.plays }.thenByDescending { it.lastPlayedMs }
        ).take(limit)
    }

    fun mostPlayedArtists(limit: Int = 5): List<TrackedGroup> = groupedBy(limit) { it.artist }

    fun mostPlayedAlbums(limit: Int = 5): List<TrackedGroup> = groupedBy(limit) { it.album }

    fun recentHistory(limit: Int = 20): List<ListeningEvent> =
        (0 until HISTORY_LIMIT).mapNotNull { index ->
            preferences.getString(historyKey(index), null)?.let { raw ->
                runCatching {
                    val event = JSONObject(raw)
                    ListeningEvent(
                        title = event.optString("title"),
                        artist = event.optString("artist"),
                        album = event.optString("album"),
                        timestampMs = event.optLong("timestamp"),
                    )
                }.getOrNull()
            }
        }.take(limit.coerceAtLeast(0))

    /**
     * Finalize a session after the last playback tick has been idle longer than the session gap.
     * This also runs when Insights is opened after playback has already gone idle.
     */
    private fun finishSessionIfIdle(now: Long) {
        val lastTick = preferences.getLong(KEY_LAST_TICK_MS, 0L)
        val sessionStart = preferences.getLong(KEY_SESSION_START_MS, 0L)
        if (
            lastTick <= 0L ||
                sessionStart <= 0L ||
                now <= lastTick ||
                now - lastTick <= SESSION_GAP_MS
        ) {
            return
        }

        val completedDuration = preferences.getLong(KEY_COMPLETED_SESSION_MS, 0L)
        val completedCount = preferences.getLong(KEY_COMPLETED_SESSION_COUNT, 0L)
        val sessionDuration = preferences.getLong(KEY_SESSION_DURATION_MS, 0L)
        preferences.edit()
            .putLong(KEY_COMPLETED_SESSION_MS, completedDuration + sessionDuration)
            .putLong(KEY_COMPLETED_SESSION_COUNT, completedCount + 1L)
            .putLong(KEY_SESSION_START_MS, 0L)
            .putLong(KEY_SESSION_DURATION_MS, 0L)
            .putLong(KEY_LAST_TICK_MS, 0L)
            .apply()
    }

    private fun groupedBy(limit: Int, name: (TrackedSong) -> String): List<TrackedGroup> {
        if (limit <= 0) return emptyList()
        return mostPlayed(Int.MAX_VALUE)
            .filter { name(it).isNotBlank() }
            .groupBy(name)
            .map { (groupName, songs) ->
                TrackedGroup(
                    name = groupName,
                    plays = songs.sumOf { it.plays },
                    listeningMs = songs.sumOf { it.listeningMs },
                )
            }.sortedByDescending { it.plays }.take(limit)
    }

    private fun trackId(item: MediaItem): String =
        item.mediaId.ifBlank { item.mediaMetadata.title?.toString().orEmpty() }

    private fun trackKey(id: String, field: String) = "${TRACK_PREFIX}${id}_$field"

    private fun key(id: String, field: String) = trackKey(id.hashCode().toString(), field)

    private fun historyKey(index: Int) = "history_$index"

    private companion object {
        const val KEY_TOTAL_MS = "total_listening_ms"
        const val KEY_LAST_LISTEN_DAY = "last_listen_day"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_LAST_TICK_MS = "last_tick_ms"
        const val KEY_SESSION_START_MS = "session_start_ms"
        const val KEY_SESSION_DURATION_MS = "session_duration_ms"
        const val KEY_COMPLETED_SESSION_MS = "completed_session_ms"
        const val KEY_COMPLETED_SESSION_COUNT = "completed_session_count"
        const val SESSION_GAP_MS = 5 * 60 * 1000L
        const val TRACK_PREFIX = "track_"
        const val HISTORY_LIMIT = 100
    }
}
