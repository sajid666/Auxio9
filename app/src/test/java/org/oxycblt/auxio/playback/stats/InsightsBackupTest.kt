/*
 * Copyright (c) 2026 Auxio Project
 * InsightsBackupTest.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.playback.stats

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class InsightsBackupTest {
    private lateinit var context: Context
    private val preferences
        get() = context.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences.edit().clear().commit()
    }

    @Test
    fun exportAndRestorePreserveListeningStatisticsAndHistory() {
        preferences.edit()
            .putLong("total_listening_ms", 42_000L)
            .putLong("streak_days", 4L)
            .putLong("track_song_plays", 3L)
            .putString("history_0", """{"title":"Test track","timestamp":1700000000000}""")
            .putLong("backup_data_updated_at", 1_700_000_000_000L)
            .commit()
        val backup = InsightsBackup.export(context, now = 1_700_000_000_100L)

        preferences.edit().clear().commit()
        val result = InsightsBackup.restore(context, backup)

        assertTrue(result.restored)
        assertEquals(42_000L, preferences.getLong("total_listening_ms", 0L))
        assertEquals(4L, preferences.getLong("streak_days", 0L))
        assertEquals(3L, preferences.getLong("track_song_plays", 0L))
        assertTrue(preferences.getString("history_0", "").orEmpty().contains("Test track"))
        assertEquals(1_700_000_000_000L, preferences.getLong("backup_data_updated_at", 0L))
    }

    @Test
    fun olderBackupCannotOverwriteNewerLocalStatistics() {
        preferences.edit()
            .putLong("total_listening_ms", 10_000L)
            .putLong("backup_data_updated_at", 1_700_000_000_000L)
            .commit()
        val oldBackup = """{
            "format":"auxio-insights-backup",
            "formatVersion":1,
            "exportedAt":1699999999000,
            "dataUpdatedAt":1699999999000,
            "data":{"total_listening_ms":{"type":"long","value":1000}}
        }"""

        val result = runCatching { InsightsBackup.restore(context, oldBackup) }

        assertFalse(result.isSuccess)
        assertEquals(10_000L, preferences.getLong("total_listening_ms", 0L))
    }

    @Test
    fun restoreRejectsUnknownBackupFormat() {
        val result = runCatching { InsightsBackup.restore(context, """{"format":"other"}""")

        assertFalse(result.isSuccess)
    }

    @Test
    fun malformedEntryDoesNotClearExistingLocalStatistics() {
        preferences.edit()
            .putLong("total_listening_ms", 25_000L)
            .putString("history_0", """{"title":"Keep me"}""")
            .putLong("backup_data_updated_at", 1_700_000_000_000L)
            .commit()
        val malformedBackup = """{
            "format":"auxio-insights-backup",
            "formatVersion":1,
            "dataUpdatedAt":1700000000000,
            "data":{
                "total_listening_ms":{"type":"long","value":1000},
                "broken":{"type":"unsupported","value":true}
            }
        }"""

        val result = runCatching { InsightsBackup.restore(context, malformedBackup) }

        assertFalse(result.isSuccess)
        assertEquals(25_000L, preferences.getLong("total_listening_ms", 0L))
        assertEquals("""{"title":"Keep me"}""", preferences.getString("history_0", null))
    }
}
