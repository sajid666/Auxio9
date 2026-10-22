/*
 * Copyright (c) 2026 Auxio Project
 * GlassBackgroundViewTest.kt is part of Auxio.
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

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GlassBackgroundViewTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun refreshBackgroundAppliesChangedStyleWithoutRecreatingView() {
        val view = GlassBackgroundView(context)
        val styleKey = context.getString(R.string.set_key_glass_background_style)
        val solidColorKey = context.getString(R.string.set_key_glass_background_solid_color)

        preferences.edit { putString(styleKey, "off") }
        view.refreshBackground()
        assertEquals(View.GONE, view.visibility)

        preferences.edit {
            putString(styleKey, "solid")
            putString(solidColorKey, "#123456")
        }
        view.refreshBackground()

        assertEquals(View.VISIBLE, view.visibility)
        val background = view.getChildAt(0).background as ColorDrawable
        assertEquals(Color.parseColor("#123456"), background.color)
    }
}
