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
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import java.io.ByteArrayOutputStream
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
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

    private val createBackupDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackup(uri)
        }

    private val openBackupDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) restoreBackup(uri)
        }

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
            setPadding(0, dp(4), 0, dp(12))
        })
        val totalMs = data.totalListeningMs
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val duration = when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${totalMs / 1_000L}s"
        }
        val averageMs = data.averageSessionMs
        val averageMinutes = averageMs / 60_000L
        val averageSeconds = (averageMs % 60_000L) / 1_000L
        val averageLabel = if (averageMinutes > 0L) {
            "${averageMinutes}m ${averageSeconds}s"
        } else {
            "${averageSeconds}s"
        }

        // Keep the three summary metrics in one aligned block: listening time on the left,
        // with streak and average session stacked to the right.
        val summaryRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val summaryContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("TOTAL LISTENING TIME", 11, true).apply { alpha = 0.75f })
            addView(text(duration, 35, true).apply {
                setTextColor(resolveColor(android.R.attr.colorAccent))
                setPadding(0, dp(5), 0, dp(3))
            })
            // Use the remaining card height as breathing room so the subtitle sits near the bottom.
            addView(View(requireContext()), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            addView(text("Tracked on this device", 11, false).apply { alpha = 0.72f })
        }
        summaryRow.addView(card(summaryContent, high = true), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1.15f,
        ).apply { marginEnd = dp(5) })

        val sideMetrics = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val streakContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Listening streak", 12, true).apply { maxLines = 1 })
            addView(text(formatStreakDays(data.listeningStreakDays), 20, true).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        val averageContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Average session", 12, true).apply { maxLines = 1 })
            addView(text(averageLabel, 20, true).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        sideMetrics.addView(card(streakContent), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ).apply { bottomMargin = dp(6) })
        sideMetrics.addView(card(averageContent), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        summaryRow.addView(sideMetrics, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f,
        ).apply { marginStart = dp(5) })
        content.addView(summaryRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(152),
        ).apply { bottomMargin = dp(8) })

        // Two compact columns make the most-played tracks and top artists easier to compare.
        val rankingsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val songsColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Most played tracks", 17, true).apply {
                setPadding(0, dp(12), 0, dp(10))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        val artistsColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Top artists", 17, true).apply {
                setPadding(0, dp(12), 0, dp(10))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        }
        if (songs.isEmpty()) {
            songsColumn.addView(card(text(getString(R.string.lbl_no_stats), 12, false)), marginParams(bottom = 8))
        } else {
            songs.forEachIndexed { index, song ->
                addRankingCard(
                    songsColumn,
                    index,
                    song.title.ifBlank { "Unknown track" },
                    song.artist.ifBlank { "Unknown artist" },
                    song.plays,
                )
            }
        }
        if (artists.isEmpty()) {
            artistsColumn.addView(card(text(getString(R.string.lbl_no_stats), 12, false)), marginParams(bottom = 8))
        } else {
            artists.forEachIndexed { index, artist ->
                addRankingCard(artistsColumn, index, artist.name.ifBlank { "Unknown artist" }, null, artist.plays)
            }
        }
        rankingsRow.addView(songsColumn, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginEnd = dp(4) })
        rankingsRow.addView(artistsColumn, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginStart = dp(4) })
        content.addView(rankingsRow, marginParams(bottom = 8))

        addBackupActions()
    }

    private fun addBackupActions() {
        addSection("Backup & restore")
        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        actions.addView(card(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Create backup", 14, true))
            addView(text("Save Insights data", 11, false).apply {
                alpha = 0.72f
                minLines = 2
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { showPrepareForReinstallDialog() }
        }, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
            marginEnd = dp(5)
        })
        actions.addView(card(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Restore backup", 14, true))
            addView(text("Import saved statistics", 11, false).apply {
                alpha = 0.72f
                minLines = 2
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                openBackupDocument.launch(arrayOf("application/json"))
            }
        }, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
            marginStart = dp(5)
        })
        content.addView(actions, marginParams(bottom = 20))
    }

    private fun showPrepareForReinstallDialog() {
        val keepData = CheckBox(requireContext()).apply {
            text = "Keep Insights data"
            isChecked = true
        }
        val message = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(
                "Create a backup before uninstalling or reinstalling Auxio. The backup includes listening time, streaks, play counts, session statistics, and recent history. Save it somewhere you can access after reinstalling, such as Google Drive.",
                14,
                false,
            ))
            addView(keepData)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Prepare for reinstall")
            .setView(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                if (keepData.isChecked) {
                    createBackupDocument.launch("Auxio-Insights-backup.json")
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No Insights backup will be created. Your statistics may be lost when the app is uninstalled.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .show()
    }

    private fun writeBackup(uri: Uri) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) {
                    InsightsBackup.export(appContext)
                }
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(snapshot.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the selected backup location.")
                }
            }
            Toast.makeText(
                appContext,
                if (result.isSuccess) {
                    "Insights backup saved. Keep this file to restore your statistics after reinstalling."
                } else {
                    "Backup failed: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}"
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun restoreBackup(uri: Uri) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8_192)
                        var totalBytes = 0
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= InsightsBackup.MAX_BACKUP_BYTES) {
                                "This backup is too large to import safely."
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } ?: error("Could not read the selected backup file.")
                }
                withContext(Dispatchers.IO) {
                    InsightsBackup.restore(appContext, snapshot)
                }
            }
            val message = if (result.isSuccess) {
                "Insights data restored."
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Could not restore this backup."
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            if (result.isSuccess && isAdded && view != null) refreshStats()
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

    /** Add a consistently sized ranking card with an intentional rank badge and aligned text. */
    private fun addRankingCard(
        parent: LinearLayout,
        index: Int,
        title: String,
        subtitle: String?,
        plays: Long,
    ) {
        val accent = resolveColor(android.R.attr.colorAccent)
        val details = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(requireContext()).apply {
                text = "#${index + 1}"
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(accent)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(withAlpha(accent, 36))
                    setStroke(dp(1), withAlpha(accent, 90))
                }
                setPadding(dp(5), dp(1), dp(5), dp(1))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(18)))
            addView(text(title, 13, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })
            if (subtitle != null) {
                addView(text(subtitle, 11, false).apply {
                    alpha = 0.72f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
            }
            // Anchor play counts at the bottom so they line up across both columns.
            addView(View(requireContext()), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            addView(text(formatPlayCount(plays), 11, false).apply {
                alpha = 0.82f
                setPadding(0, dp(2), 0, 0)
            })
        }
        parent.addView(card(details), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(100),
        ).apply {
            bottomMargin = dp(8)
            marginStart = dp(2)
            marginEnd = dp(2)
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
            if (high) {
                addView(child, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } else {
                addView(child)
            }
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
