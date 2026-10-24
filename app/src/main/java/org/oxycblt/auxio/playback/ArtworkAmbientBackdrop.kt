/*
 * Copyright (c) 2026 Auxio Project
 * ArtworkAmbientBackdrop.kt is part of Auxio.
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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.util.CoilUtils
import org.oxycblt.musikr.Song

/**
 * A blurred, song-specific artwork layer sampled by the player glass surfaces.
 *
 * On Android 12 and newer the blur is applied by RenderEffect. On older versions the image remains
 * softly translucent, so it still provides album-derived color without requiring extra dependencies.
 */
internal class ArtworkAmbientBackdrop(
    context: Context,
    private val imageLoader: ImageLoader,
    blurRadiusDp: Float = 30f,
    opacity: Float = 0.78f,
) : AppCompatImageView(context) {
    init {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = opacity
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        visibility = View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = blurRadiusDp * resources.displayMetrics.density
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
    }

    /** Load the current song cover, using Coil's cache and existing cover fetcher. */
    fun bind(song: Song?) {
        CoilUtils.dispose(this)
        val cover = song?.cover
        if (cover == null) {
            setImageDrawable(null)
            visibility = View.GONE
            return
        }

        visibility = View.VISIBLE
        imageLoader.enqueue(ImageRequest.Builder(context).data(cover).target(this).build())
    }

    /** Release the image request when the view is no longer owned by a fragment. */
    fun clear() {
        CoilUtils.dispose(this)
        setImageDrawable(null)
        visibility = View.GONE
    }
}
