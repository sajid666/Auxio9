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
            setPadding(0, 0, 0, 0)
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
        val totalMs = tracker.totalListeningMs()
        val songs = tracker.mostPlayed(10)

        content.addView(text("Listening insights", 28, true))
        content.addView(text(getString(R.string.lbl_stats_since), 14, false).apply {
            alpha = 0.72f
            setPadding(0, dp(4), 0, dp(16))
        })

        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val duration = when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${totalMs / 1_000L}s"
        }
        val summary = MaterialCardView(requireContext()).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            setContentPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val summaryContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        summaryContent.addView(text("TOTAL LISTENING TIME", 12, true).apply { alpha = 0.75f })
        summaryContent.addView(text(duration, 36, true).apply {
            setPadding(0, dp(4), 0, dp(2))
        })
        summaryContent.addView(text("Tracked on this device", 14, false).apply { alpha = 0.72f })
        summary.addView(summaryContent)
        content.addView(summary, marginParams(bottom = 24))

        content.addView(text(getString(R.string.lbl_most_played), 21, true).apply {
            setPadding(0, 0, 0, dp(10))
        })

        if (songs.isEmpty()) {
            val empty = MaterialCardView(requireContext()).apply {
                radius = dp(20).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
                setContentPadding(dp(18), dp(18), dp(18), dp(18))
            }
            empty.addView(text(getString(R.string.lbl_no_stats), 15, false))
            content.addView(empty, marginParams(bottom = 12))
            return
        }

        songs.forEachIndexed { index, song ->
            val row = MaterialCardView(requireContext()).apply {
                radius = dp(20).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceContainer))
                setContentPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val line = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val rank = text((index + 1).toString().padStart(2, '0'), 14, true).apply {
                gravity = Gravity.CENTER
                alpha = 0.72f
            }
            line.addView(rank, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
            val details = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            details.addView(text(song.title.ifBlank { "Unknown track" }, 16, true))
            val byline = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · ")
            details.addView(text(byline.ifBlank { "Unknown artist" }, 13, false).apply {
                alpha = 0.72f
                setPadding(0, dp(3), 0, dp(5))
            })
            val listenedMinutes = song.listeningMs / 60_000L
            details.addView(text("${song.plays} plays  ·  ${listenedMinutes} min listened", 12, false).apply {
                alpha = 0.78f
            })
            line.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(line)
            content.addView(row, marginParams(bottom = 8))
        }
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
