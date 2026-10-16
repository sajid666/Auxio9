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

/**
 * Local, install-forward listening statistics. Data stays in app-private preferences.
 * Playback time is added only while ExoPlayer reports that audio is actually playing.
 */
class PlaybackStatsTracker(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)

    fun recordSecond(item: MediaItem?) {
        item ?: return
        val metadata = item.mediaMetadata
        val id = item.mediaId.ifBlank { metadata.title?.toString().orEmpty() }
        if (id.isBlank()) return

        val editor = preferences.edit()
        editor.putLong(KEY_TOTAL_MS, preferences.getLong(KEY_TOTAL_MS, 0L) + 1_000L)
        editor.putLong(key(id, "time"), preferences.getLong(key(id, "time"), 0L) + 1_000L)
        editor.putLong(key(id, "last"), System.currentTimeMillis())
        editor.apply()
    }

    /** Count a play when a track starts, rather than on every progress tick. */
    fun recordPlay(item: MediaItem?) {
        item ?: return
        val metadata = item.mediaMetadata
        val id = item.mediaId.ifBlank { metadata.title?.toString().orEmpty() }
        if (id.isBlank()) return

        preferences.edit()
            .putLong(key(id, "plays"), preferences.getLong(key(id, "plays"), 0L) + 1L)
            .putString(key(id, "title"), metadata.title?.toString().orEmpty())
            .putString(key(id, "artist"), metadata.artist?.toString().orEmpty())
            .putString(key(id, "album"), metadata.albumTitle?.toString().orEmpty())
            .putLong(key(id, "last"), System.currentTimeMillis())
            .apply()
    }

    fun totalListeningMs(): Long = preferences.getLong(KEY_TOTAL_MS, 0L)

    /** Returns songs ordered by play count, with the most-played song first. */
    fun mostPlayed(limit: Int = 10): List<TrackedSong> {
        if (limit <= 0) return emptyList()
        val ids = preferences.all.keys
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
        }.sortedWith(compareByDescending<TrackedSong> { it.plays }.thenByDescending { it.lastPlayedMs })
            .take(limit)
    }

    private fun trackKey(id: String, field: String) = "${TRACK_PREFIX}${id}_$field"

    private fun key(id: String, field: String) = trackKey(id.hashCode().toString(), field)

    private companion object {
        const val KEY_TOTAL_MS = "total_listening_ms"
        const val TRACK_PREFIX = "track_"
    }
}
