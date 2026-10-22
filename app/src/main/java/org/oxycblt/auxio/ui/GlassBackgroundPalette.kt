/*
 * Copyright (c) 2026 Auxio Project
 * GlassBackgroundPalette.kt is part of Auxio.
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
 
package org.oxycblt.auxio.ui

/** Stable presets and bounds shared by the glass background view and its tests. */
internal object GlassBackgroundPalette {
    private val presets =
        mapOf(
            "aurora" to listOf("#10172A", "#36527F", "#91578B"),
            "sunset" to listOf("#28152F", "#A74962", "#E5A36C"),
            "ocean" to listOf("#071D2D", "#0A7185", "#546CB3"),
            "emerald" to listOf("#071F1A", "#246851", "#84A77A"),
            "monochrome" to listOf("#0D1018", "#3A3E4C", "#868B9C"),
        )

    fun colorsForPreset(preset: String): List<String> =
        presets[preset] ?: presets.getValue("aurora")

    fun normalizeBlurRadius(value: Int): Int = value.coerceIn(0, 48)

    fun normalizeDarkness(value: Int): Int = value.coerceIn(0, 70)
}
