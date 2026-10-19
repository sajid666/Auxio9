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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.card.MaterialCardView
import org.oxycblt.auxio.R

/** Data snapshot loaded off the UI thread before rendering the Insights screen. */
private data class InsightsData(
    val songs: List<TrackedSong>,
    val artists: List<TrackedGroup>,
    val totalListeningMs: Long,
    val listeningStreakDays: Long,
    val averageSessionMs: Long,
)

/** A local overview of listening history collected by the playback service. */
class InsightsFragment : Fragment() {
    private lateinit var content: LinearLayout
    private var renderJob: Job? = null

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
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(16), systemBars.top + dp(12), dp(16), dp(28))
            insets
        }
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        renderJob = null
        super.onDestroyView()
    }

    /**
     * Read and rank saved statistics off the main thread. Replacing the current job prevents
     * repeated resumes from queueing multiple expensive reads for the same view.
     */
    private fun refreshStats() {
        if (!::content.isInitialized) return
        renderJob?.cancel()
        val appContext = requireContext().applicationContext
        renderJob =
            viewLifecycleOwner.lifecycleScope.launch {
                val data =
                    withContext(Dispatchers.Default) {
                        val tracker = PlaybackStatsTracker(appContext)
                        InsightsData(
                            songs = tracker.mostPlayed(3),
                            artists = tracker.mostPlayedArtists(3),
                            totalListeningMs = tracker.totalListeningMs(),
                            listeningStreakDays = tracker.listeningStreakDays(),
                            averageSessionMs = tracker.averageSessionMs(),
                        )
                    }
                renderStats(data)
            }
    }

    private fun renderStats(data: InsightsData) {
        content.removeAllViews()
        val songs = data.songs
        val artists = data.artists

        content.addView(text("Listening insights", 28, true).apply { setTextColor(resolveColor(android.R.attr.colorAccent)) })
        content.addView(text(getString(R.string.lbl_stats_since), 14, false).apply {
            alpha = 0.72f
            setPadding(0, dp(4), 0, dp(16))
        })

        val totalMs = data.totalListeningMs
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
            setTextColor(resolveColor(android.R.attr.colorAccent))
            setPadding(0, dp(4), 0, dp(2))
        })
        summaryContent.addView(text("Tracked on this device", 14, false).apply { alpha = 0.72f })
        content.addView(card(summaryContent, high = true), marginParams(bottom = 12))

        val averageMs = data.averageSessionMs
        val averageMinutes = averageMs / 60_000L
        val averageSeconds = (averageMs % 60_000L) / 1_000L
        val averageLabel = if (averageMinutes > 0L) {
            "${averageMinutes}m ${averageSeconds}s"
        } else {
            "${averageSeconds}s"
        }

        val summaryRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val streakColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Listening streak", 16, true).apply {
                setPadding(0, 0, 0, dp(8))
            })
            val value = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(formatStreakDays(data.listeningStreakDays), 22, true))
            }
            addView(card(value), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val averageColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Average session", 16, true).apply {
                setPadding(0, 0, 0, dp(8))
            })
            val value = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(averageLabel, 22, true))
            }
            addView(card(value), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        summaryRow.addView(streakColumn, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginEnd = dp(5) })
        summaryRow.addView(averageColumn, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginStart = dp(5) })
        content.addView(summaryRow, marginParams(bottom = 8))

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
                detail.addView(text(formatPlayCount(song.plays), 12, false).apply { alpha = 0.78f })
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
                    addView(text(formatPlayCount(artist.plays), 12, false))
                })
            }
        }

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
        line.addView(details, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        content.addView(card(line), marginParams(bottom = 8).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
        })
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
            setCardBackgroundColor(withAlpha(surfaceColor, if (high) 238 else 218))
            strokeColor = withAlpha(
                resolveColor(android.R.attr.colorAccent),
                if (high) 170 else 95,
            )
            setContentPadding(dp(14), dp(12), dp(14), dp(12))
            addView(child)
        }

    private fun text(value: String, sizeSp: Int, bold: Boolean): TextView =
        TextView(requireContext()).apply {
            text = value
            textSize = sizeSp.toFloat()
            maxWidth = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt().let {
                dp((it - 80).coerceAtLeast(180))
            }
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

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
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(bottom)
            marginStart = dp(8)
            marginEnd = dp(8)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** Format a listening streak with correct singular/plural wording. */
internal fun formatStreakDays(days: Long): String =
    if (days == 1L) "1 day" else "$days days"

/** Format a play count without the incorrect singular "plays" label. */
internal fun formatPlayCount(plays: Long): String =
    if (plays == 1L) "1 play" else "$plays plays"
