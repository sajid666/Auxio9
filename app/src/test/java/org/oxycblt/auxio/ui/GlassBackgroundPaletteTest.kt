/*
 * Copyright (c) 2026 Auxio Project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.oxycblt.auxio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBackgroundPaletteTest {
    @Test
    fun presetHasThreeColorsAndUnknownPresetFallsBackToAurora() {
        assertEquals(3, GlassBackgroundPalette.colorsForPreset("ocean").size)
        assertEquals(
            GlassBackgroundPalette.colorsForPreset("aurora"),
            GlassBackgroundPalette.colorsForPreset("unknown"),
        )
    }

    @Test
    fun allPresetsContainThreeHexColors() {
        listOf("aurora", "sunset", "ocean", "emerald", "monochrome").forEach { preset ->
            val colors = GlassBackgroundPalette.colorsForPreset(preset)
            assertEquals(3, colors.size)
            colors.forEach { color -> assertTrue(color.matches(Regex("#[0-9A-Fa-f]{6}"))) }
        }
    }

    @Test
    fun blurAndDarknessAreClampedToSupportedRanges() {
        assertEquals(0, GlassBackgroundPalette.normalizeBlurRadius(-5))
        assertEquals(48, GlassBackgroundPalette.normalizeBlurRadius(90))
        assertEquals(0, GlassBackgroundPalette.normalizeDarkness(-20))
        assertEquals(70, GlassBackgroundPalette.normalizeDarkness(90))
    }
}
