/*
 * Copyright (c) 2022 Auxio Project
 * PlaybackBarFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.playback

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import coil3.ImageLoader
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentPlaybackBarBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [ViewBindingFragment] that shows the current playback state in a compact manner.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class PlaybackBarFragment : ViewBindingFragment<FragmentPlaybackBarBinding>() {
    @Inject lateinit var imageLoader: ImageLoader

    private var artworkAmbientBackdrop: ArtworkAmbientBackdrop? = null
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentPlaybackBarBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentPlaybackBarBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- UI SETUP ---
        binding.root.apply {
            setOnClickListener { playbackModel.openPlayback() }
            setOnLongClickListener {
                playbackModel.song.value?.let(detailModel::showAlbum)
                true
            }
        }

        // Add the liquid material behind the compact player content. The cover is its color source;
        // the transparent surface lets the lens pick up that color without covering the controls.
        installMiniPlayerGlass(binding)

        // Set up marquee on song information
        binding.playbackSong.isSelected = true
        binding.playbackInfo.isSelected = true

        // Set up actions
        binding.playbackPlayPause.setOnClickListener { playbackModel.togglePlaying() }

        // -- VIEWMODEL SETUP ---
        collectImmediately(playbackModel.song, ::updateSong)
        collectImmediately(playbackModel.isPlaying, ::updatePlaying)
        collectImmediately(playbackModel.positionDs, ::updatePosition)
        collectImmediately(
            playbackModel.currentBarAction,
            playbackModel.repeatMode,
            playbackModel.isShuffled,
            ::updateBarAction,
        )
    }

    override fun onDestroyBinding(binding: FragmentPlaybackBarBinding) {
        artworkAmbientBackdrop?.clear()
        artworkAmbientBackdrop = null
        super.onDestroyBinding(binding)
        binding.playbackSecondaryAction.clearPendingIcon()
        // Marquee elements leak if they are not disabled when the views are destroyed.
        binding.playbackSong.isSelected = false
        binding.playbackInfo.isSelected = false
    }

    private fun installMiniPlayerGlass(binding: FragmentPlaybackBarBinding) {
        val density = resources.displayMetrics.density
        val ambient =
            ArtworkAmbientBackdrop(
                    requireContext(),
                    imageLoader,
                    blurRadiusDp = 24f,
                    opacity = 0.72f,
                )
                .apply {
                    id = View.generateViewId()
                }
        val ambientParams =
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                val outset = (8f * density).toInt()
                setMargins(-outset, -outset, -outset, -outset)
            }
        binding.root.addView(ambient, 0, ambientParams)
        artworkAmbientBackdrop = ambient

        val glass =
            MiniPlayerLiquidGlassOverlay(requireContext()).apply {
                id = View.generateViewId()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
                isFocusable = false
                enablePressEffect = false
                backdropSource = ambient
                enableDynamicBackground = true
                enableBackdropBlur = true
                enableChromaticAberration = false
                enableChromaticDispersion = true
                enableEdgeHighlight = true
                enableSensorHighlight = true
                enableAdaptiveTint = false
                material = GlassMaterial.CLEAR
                cornerRadius = 28f * density
                bevelWidth = 20f * density
                refractionHeight = 40f * density
                refractionFalloff = 1.5f
                dispersionStrength = 0.14f
                blurAmount = 0.075f
                saturation = 140f
                collectFrameStats = false
            }
        val params =
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.root.addView(glass, 1, params)
    }

    private fun updateSong(song: Song?) {
        artworkAmbientBackdrop?.bind(song)
        if (song == null) {
            // Nothing to do.
            return
        }

        val context = requireContext()
        val binding = requireBinding()
        binding.playbackCover.bind(song)
        binding.playbackSong.text = song.name.resolve(context)
        binding.playbackInfo.text = song.artists.resolveNames(context)
        binding.playbackProgressBar.max = song.durationMs.msToDs().toInt()
    }

    private fun updatePlaying(isPlaying: Boolean) {
        requireBinding().playbackPlayPause.isChecked = isPlaying
    }

    private fun updatePosition(positionDs: Long) {
        requireBinding().playbackProgressBar.progress = positionDs.toInt()
    }

    private fun updateBarAction(
        actionMode: ActionMode,
        repeatMode: RepeatMode,
        isShuffled: Boolean,
    ) {
        val binding = requireBinding()
        when (actionMode) {
            ActionMode.NEXT -> {
                L.d("Using skip next action")
                binding.playbackSecondaryAction.apply {
                    if (tag != actionMode) {
                        setIconResource(R.drawable.ic_skip_next_24)
                        contentDescription = getString(R.string.desc_skip_next)
                        setOnClickListener { playbackModel.next() }
                        isChecked = false
                        tag = actionMode
                    }
                }
            }
            ActionMode.REPEAT -> {
                L.d("Using repeat mode action")
                binding.playbackSecondaryAction.apply {
                    if (tag != actionMode) {
                        contentDescription = getString(R.string.desc_change_repeat)
                        setOnClickListener { playbackModel.toggleRepeatMode() }
                        tag = actionMode
                    }
                    setIconResource(repeatMode.icon)
                    isChecked = repeatMode != RepeatMode.NONE
                }
            }
            ActionMode.SHUFFLE -> {
                L.d("Using shuffle action")
                binding.playbackSecondaryAction.apply {
                    if (tag != actionMode) {
                        setIconResource(R.drawable.sel_shuffle_state_24)
                        contentDescription = getString(R.string.desc_shuffle)
                        setOnClickListener { playbackModel.toggleShuffled() }
                        tag = actionMode
                    }
                    isChecked = isShuffled
                }
            }
        }
    }
}

/** A visual-only lens that does not consume taps intended for the mini-player controls. */
private class MiniPlayerLiquidGlassOverlay(context: Context) : LiquidGlassView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
