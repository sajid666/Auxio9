/*
 * Copyright (c) 2026 Auxio Project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.preference.PreferenceManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.oxycblt.auxio.R
import timber.log.Timber as L

/**
 * A single background layer shared behind library lists. It reacts to preferences, so changes
 * made under Look and feel appear without rebuilding the app.
 */
class GlassBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val imageView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = null
    }
    private val dimOverlay = View(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }
    private val imageRequestId = AtomicInteger()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey in backgroundPreferenceKeys) {
            post { renderBackground() }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(dimOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        renderBackground()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        imageRequestId.incrementAndGet()
        super.onDetachedFromWindow()
    }

    private fun renderBackground() {
        val style = preferences.getString(key(R.string.set_key_glass_background_style), "preset") ?: "preset"
        if (style == "off") {
            visibility = View.GONE
            imageRequestId.incrementAndGet()
            return
        }
        visibility = View.VISIBLE

        imageView.setImageDrawable(null)
        imageView.background = null
        val gradient = when (style) {
            "preset" -> createGradient(
                GlassBackgroundPalette.colorsForPreset(
                    preferences.getString(key(R.string.set_key_glass_background_preset), "aurora") ?: "aurora",
                ),
            )
            "custom" -> createGradient(
                listOf(
                    colorPreference(R.string.set_key_glass_background_color_one, "#10172A"),
                    colorPreference(R.string.set_key_glass_background_color_two, "#36527F"),
                    colorPreference(R.string.set_key_glass_background_color_three, "#91578B"),
                ),
            )
            "image" -> createGradient(GlassBackgroundPalette.colorsForPreset("aurora"))
            "solid" -> ColorDrawable(
                parseColor(
                    colorPreference(R.string.set_key_glass_background_solid_color, "#10172A"),
                    Color.rgb(16, 23, 42),
                ),
            )
            else -> createGradient(GlassBackgroundPalette.colorsForPreset("aurora"))
        }
        imageView.background = gradient

        applyBlur()
        applyDarkness()

        if (style == "image") {
            val selectedUri = preferences.getString(key(R.string.set_key_glass_background_image), null)
            if (selectedUri.isNullOrBlank()) {
                visibility = View.GONE
                imageRequestId.incrementAndGet()
                return
            }
            loadSelectedImage(selectedUri)
        } else {
            imageRequestId.incrementAndGet()
        }
    }

    private fun createGradient(colors: List<String>): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, parseColors(colors))

    private fun applyBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurDp = GlassBackgroundPalette.normalizeBlurRadius(
                preferences.getInt(key(R.string.set_key_glass_background_blur), 28),
            )
            val radiusPx = blurDp * resources.displayMetrics.density
            imageView.setRenderEffect(
                if (radiusPx > 0f) {
                    RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                } else {
                    null
                },
            )
        }
    }

    private fun applyDarkness() {
        val darkness = GlassBackgroundPalette.normalizeDarkness(
            preferences.getInt(key(R.string.set_key_glass_background_darkness), 35),
        )
        dimOverlay.setBackgroundColor(Color.argb(darkness * 255 / 100, 0, 0, 0))
    }

    private fun loadSelectedImage(uriString: String) {
        val request = imageRequestId.incrementAndGet()
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val targetWidth = measuredWidth.coerceAtLeast(resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val targetHeight = measuredHeight.coerceAtLeast(resources.displayMetrics.heightPixels).coerceAtLeast(1)
        IMAGE_EXECUTOR.execute {
            var bitmap: Bitmap? = null
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    resolver.openInputStream(uri)?.use { bitmap = BitmapFactory.decodeStream(it, null, options) }
                }
            } catch (exception: Exception) {
                L.w(exception, "Unable to load glass background image")
            }
            val decoded = bitmap
            post {
                if (request != imageRequestId.get() ||
                    preferences.getString(key(R.string.set_key_glass_background_image), null) != uriString ||
                    preferences.getString(key(R.string.set_key_glass_background_style), "preset") != "image"
                ) {
                    decoded?.recycle()
                    return@post
                }
                if (decoded != null) {
                    imageView.setImageBitmap(decoded)
                } else {
                    imageView.setImageDrawable(null)
                    L.w("Could not decode selected glass background image; showing gradient fallback")
                }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth || height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun colorPreference(keyRes: Int, fallback: String): String =
        preferences.getString(key(keyRes), fallback) ?: fallback

    private fun parseColors(values: List<String>): IntArray {
        val fallbacks = intArrayOf(
            Color.rgb(16, 23, 42),
            Color.rgb(54, 82, 127),
            Color.rgb(145, 87, 139),
        )
        return values.mapIndexed { index, value ->
            parseColor(value, fallbacks.getOrElse(index) { Color.rgb(16, 23, 42) })
        }.toIntArray()
    }

    private fun parseColor(value: String, fallback: Int): Int =
        runCatching { Color.parseColor(value) }.getOrDefault(fallback)

    private fun key(keyRes: Int): String = context.getString(keyRes)

    private companion object {
        val IMAGE_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "auxio-glass-background").apply { isDaemon = true }
        }
        val backgroundPreferenceKeys = setOf(
            "auxio_glass_background_style",
            "auxio_glass_background_preset",
            "auxio_glass_background_color_one",
            "auxio_glass_background_color_two",
            "auxio_glass_background_color_three",
            "auxio_glass_background_solid_color",
            "auxio_glass_background_image",
            "auxio_glass_background_blur",
            "auxio_glass_background_darkness",
        )
    }
}
