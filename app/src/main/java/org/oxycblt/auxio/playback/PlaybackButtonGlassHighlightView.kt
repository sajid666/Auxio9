/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackButtonGlassHighlightView.kt is part of Auxio.
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View

/**
 * Adds broad, curved specular reflections over a playback-button LiquidGlassView.
 *
 * The dependency's AGSL lens supplies backdrop refraction. Its built-in rim is intentionally
 * narrow, so this independent AGSL pass supplies the missing surface reflections without changing
 * the lens, button drawable, icon, or touch target.
 */
internal class PlaybackButtonGlassHighlightView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
                uniform float2 size;
                uniform float radius;

                float roundedRectDistance(float2 point, float2 halfSize, float r) {
                    float2 q = abs(point) - (halfSize - float2(r));
                    return length(max(q, float2(0.0))) +
                        min(max(q.x, q.y), 0.0) - r;
                }

                half4 main(float2 coord) {
                    float2 safeSize = max(size, float2(1.0));
                    float2 uv = coord / safeSize;
                    float2 point = coord - safeSize * 0.5;
                    float d = roundedRectDistance(
                        point,
                        safeSize * 0.5,
                        min(radius, min(safeSize.x, safeSize.y) * 0.5)
                    );

                    // A broad upper reflection, gently curved rather than a second outline.
                    float curveY = 0.16 + 0.11 * pow((uv.x - 0.34) * 1.55, 2.0);
                    float upperArc = exp(-pow((uv.y - curveY) / 0.055, 2.0));
                    upperArc *= smoothstep(0.02, 0.16, uv.x);
                    upperArc *= 1.0 - smoothstep(0.70, 0.88, uv.x);

                    // A soft side reflection gives the rounded edge visible volume.
                    float sideX = 0.075 + 0.035 * sin(uv.y * 3.2);
                    float sideArc = exp(-pow((uv.x - sideX) / 0.035, 2.0));
                    sideArc *= smoothstep(0.08, 0.24, uv.y);
                    sideArc *= 1.0 - smoothstep(0.72, 0.94, uv.y);

                    // A restrained lower-right reflection balances the upper highlight.
                    float lowerCurve = 0.88 - 0.07 * pow((uv.x - 0.76) * 1.5, 2.0);
                    float lowerArc = exp(-pow((uv.y - lowerCurve) / 0.045, 2.0));
                    lowerArc *= smoothstep(0.54, 0.76, uv.x);

                    // Keep the reflective rim broad and soft; the lens shader handles refraction.
                    float rim = exp(-abs(d) / 5.5);
                    float rimTopLeft = smoothstep(-0.15, 0.85, -uv.y + uv.x * 0.28);
                    float rimAlpha = rim * (0.12 + 0.13 * rimTopLeft);

                    float alpha = upperArc * 0.27 +
                        sideArc * 0.13 +
                        lowerArc * 0.12 +
                        rimAlpha;
                    alpha *= 1.0 - smoothstep(0.0, 2.0, d);
                    alpha = clamp(alpha, 0.0, 0.38);

                    float3 cool = float3(0.72, 0.88, 1.0);
                    float3 warm = float3(1.0, 0.88, 0.72);
                    float warmMix = clamp(lowerArc * 0.55 + (1.0 - uv.y) * 0.08, 0.0, 0.42);
                    float3 reflection = mix(cool, warm, warmMix);
                    return half4(half3(reflection), half(alpha));
                }
                """.trimIndent()
            )
        } else {
            null
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setWillNotDraw(shader == null)
    }

    fun setCornerRadius(radiusPx: Float) {
        shader?.setFloatUniform("radius", radiusPx)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val activeShader = shader ?: return
        if (!canvas.isHardwareAccelerated || width <= 0 || height <= 0) return

        activeShader.setFloatUniform("size", width.toFloat(), height.toFloat())
        paint.shader = activeShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
