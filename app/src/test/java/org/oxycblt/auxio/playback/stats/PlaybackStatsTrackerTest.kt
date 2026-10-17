/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackStatsTrackerTest.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.playback.stats

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PlaybackStatsTrackerTest {
    private lateinit var context: Context
    private lateinit var tracker: PlaybackStatsTracker
    private var nowMs = 1_700_000_000_000L
    private var epochDay = 20_000L

    private val preferences
        get() = context.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences.edit().clear().commit()
        tracker = PlaybackStatsTracker(context, { nowMs }, { epochDay })
    }

    @Test
    fun listeningStreakExpiresAfterAnUnplayedDay() {
        tracker.recordSecond(mediaItem("song-1"))
        assertEquals(1L, tracker.listeningStreakDays())

        epochDay += 1L
        nowMs += DAY_MS
        assertEquals(1L, tracker.listeningStreakDays())

        epochDay += 1L
        nowMs += DAY_MS
        assertEquals(0L, tracker.listeningStreakDays())
        // Keep the stored historical value; only its current-streak interpretation expires.
        assertEquals(1L, preferences.getLong("streak_days", 0L))
    }

    @Test
    fun listeningStreakIncrementsOnConsecutiveDays() {
        tracker.recordSecond(mediaItem("song-1"))

        epochDay += 1L
        nowMs += DAY_MS
        tracker.recordSecond(mediaItem("song-1"))

        assertEquals(2L, tracker.listeningStreakDays())
    }

    @Test
    fun idleSessionIsFinalizedWhenAverageIsRead() {
        tracker.recordSecond(mediaItem("song-1"))
        nowMs += 1_000L
        tracker.recordSecond(mediaItem("song-1"))

        nowMs += SESSION_GAP_MS + 1L
        assertEquals(2_000L, tracker.averageSessionMs())
        assertEquals(1L, preferences.getLong("completed_session_count", 0L))
        assertEquals(2_000L, preferences.getLong("completed_session_ms", 0L))
        assertEquals(0L, preferences.getLong("session_start_ms", -1L))
        assertEquals(0L, preferences.getLong("session_duration_ms", -1L))
        assertEquals(0L, preferences.getLong("last_tick_ms", -1L))

        // Re-reading the screen must not close the same session a second time.
        assertEquals(2_000L, tracker.averageSessionMs())
        assertEquals(1L, preferences.getLong("completed_session_count", 0L))
    }

    @Test
    fun activeSessionIsIncludedBeforeIdleTimeout() {
        tracker.recordSecond(mediaItem("song-1"))
        nowMs += 1_000L
        tracker.recordSecond(mediaItem("song-1"))

        assertEquals(2_000L, tracker.averageSessionMs())
        assertEquals(0L, preferences.getLong("completed_session_count", 0L))
    }

    @Test
    fun trackAndArtistListsRespectThreeItemLimit() {
        (1..5).forEach { index ->
            tracker.recordPlay(mediaItem("song-$index", "Artist $index"))
        }

        assertEquals(3, tracker.mostPlayed(3).size)
        assertEquals(3, tracker.mostPlayedArtists(3).size)
    }

    @Test
    fun streakLabelUsesSingularOnlyForOneDay() {
        assertEquals("0 days", formatStreakDays(0L))
        assertEquals("1 day", formatStreakDays(1L))
        assertEquals("2 days", formatStreakDays(2L))
    }

    private fun mediaItem(id: String, artist: String = "Test Artist") =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Title $id")
                    .setArtist(artist)
                    .setAlbumTitle("Album $id")
                    .build()
            )
            .build()

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L
        const val SESSION_GAP_MS = 5 * 60 * 1_000L
    }
}
