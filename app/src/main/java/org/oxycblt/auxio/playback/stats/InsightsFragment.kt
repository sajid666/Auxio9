/*
 * Copyright (c) 2026 Auxio Project
 * InsightsFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.playback.stats

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import org.oxycblt.auxio.R

/** A local overview of listening history collected by the playback service. */
class InsightsFragment : Fragment() {
    private lateinit var content: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val scroll = ScrollView(context).apply {
            clipToPadding = false
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        renderStats()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) renderStats()
    }

    private fun renderStats() {
        content.removeAllViews()
        val tracker = PlaybackStatsTracker(requireContext())
        val songs = tracker.mostPlayed(10)
        val artists = tracker.mostPlayedArtists(5)

        content.addView(text("Listening insights", 28, true).apply { setTextColor(resolveColor(android.R.attr.colorAccent)) })
        content.addView(text(getString(R.string.lbl_stats_since), 14, false).apply {
            alpha = 0.72f
            setPadding(0, dp(4), 0, dp(16))
        })

        val totalMs = tracker.totalListeningMs()
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val duration = when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${totalMs / 1_000L}s"
        }
        val summaryContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        summaryContent.addView(text("TOTAL LISTENING TIME", 12, true).apply { alpha = 0.75f })
        summaryContent.addView(text(duration, 36, true).apply {
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(0, dp(4), 0, dp(2))
        })
        summaryContent.addView(text("Tracked on this device", 14, false).apply { alpha = 0.72f })
        content.addView(card(summaryContent, high = true), marginParams(bottom = 24))

        addSection("Most played tracks")
        if (songs.isEmpty()) {
            addEmptyState()
        } else {
            songs.forEachIndexed { index, song ->
                val detail = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                }
                detail.addView(text(song.title.ifBlank { "Unknown track" }, 16, true))
                detail.addView(text(
                    listOf(song.artist, song.album).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { "Unknown artist" },
                    13,
                    false,
                ).apply {
                    alpha = 0.72f
                    setPadding(0, dp(3), 0, dp(5))
                })
                detail.addView(text(
                    "${song.plays} plays · ${song.listeningMs / 60_000L} min listened",
                    12,
                    false,
                ).apply { alpha = 0.78f })
                addRankedRow(index, detail)
            }
        }

        addSection("Top artists")
        if (artists.isEmpty()) {
            addEmptyState()
        } else {
            artists.forEachIndexed { index, artist ->
                addRankedRow(index, LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(artist.name, 16, true))
                    addView(text("${artist.plays} plays · ${artist.listeningMs / 60_000L} min", 12, false))
                })
            }
        }

        addSection("Listening streak")
        val streakContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("${tracker.listeningStreakDays()} days", 28, true))
            addView(text("Consecutive days with music played", 13, false).apply { alpha = 0.72f })
        }
        content.addView(card(streakContent), marginParams(bottom = 8))

        addSection("Average session")
        val averageMs = tracker.averageSessionMs()
        val averageMinutes = averageMs / 60_000L
        val averageSeconds = (averageMs % 60_000L) / 1_000L
        val averageLabel = if (averageMinutes > 0L) {
            "${averageMinutes}m ${averageSeconds}s"
        } else {
            "${averageSeconds}s"
        }
        val averageContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(averageLabel, 28, true))
            addView(text("Average listening time per session", 13, false).apply { alpha = 0.72f })
        }
        content.addView(card(averageContent), marginParams(bottom = 8))
    }

    private fun addSection(title: String) {
        content.addView(text(title, 21, true).apply {
            setPadding(0, dp(12), 0, dp(10))
        })
    }

    private fun addEmptyState() {
        content.addView(card(text(getString(R.string.lbl_no_stats), 14, false)), marginParams(bottom = 8))
    }

    private fun addRankedRow(index: Int, details: View) {
        val line = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(text((index + 1).toString().padStart(2, '0'), 14, true).apply {
            gravity = Gravity.CENTER
            alpha = 0.72f
        }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(card(line), marginParams(bottom = 8))
    }

    private fun card(child: View, high: Boolean = false) =
        MaterialCardView(requireContext()).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            val colorAttribute = if (high) {
                com.google.android.material.R.attr.colorSurfaceContainerHigh
            } else {
                com.google.android.material.R.attr.colorSurfaceContainer
            }
            val surfaceColor = resolveColor(colorAttribute)
            // Glass-inspired translucent surfaces with a theme-colored outline.
            setCardBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, if (high) 238 else 218))
            strokeColor = android.graphics.ColorUtils.setAlphaComponent(
                resolveColor(com.google.android.material.R.attr.colorPrimary),
                if (high) 170 else 95,
            )
            setContentPadding(dp(14), dp(12), dp(14), dp(12))
            addView(child)
        }

    private fun text(value: String, sizeSp: Int, bold: Boolean): TextView =
        TextView(requireContext()).apply {
            text = value
            textSize = sizeSp.toFloat()
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        }

    private fun resolveColor(attribute: Int): Int {
        val value = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId)
        } else {
            value.data
        }
    }

    private fun marginParams(bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
