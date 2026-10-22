/*
 * Copyright (c) 2023 Auxio Project
 * UIPreferenceFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.categories

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.auxio.util.isNight
import org.oxycblt.auxio.util.navigateSafe
import timber.log.Timber as L

/**
 * Display preferences.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class UIPreferenceFragment : BasePreferenceFragment(R.xml.preferences_ui) {
    @Inject lateinit var uiSettings: UISettings

    private val glassImagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val appContext = requireContext().applicationContext
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (exception: SecurityException) {
                    L.w(exception, "Unable to persist background image permission")
                }

                PreferenceManager.getDefaultSharedPreferences(appContext).edit {
                    putString(getString(R.string.set_key_glass_background_image), uri.toString())
                    putString(getString(R.string.set_key_glass_background_style), "image")
                }
                findPreference<Preference>(getString(R.string.set_key_glass_background_image))
                    ?.summary = getString(R.string.set_glass_background_image_selected)
                findPreference<ListPreference>(getString(R.string.set_key_glass_background_style))
                    ?.value = "image"
                updateGlassPreferenceVisibility("image")
            }
        }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_accent)) {
            L.d("Navigating to accent dialog")
            findNavController().navigateSafe(UIPreferenceFragmentDirections.accentSettings())
        }
    }

    override fun onSetupPreference(preference: Preference) {
        when (preference.key) {
            getString(R.string.set_key_theme) -> {
                L.d("Configuring theme setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, value ->
                        L.d("Theme changed, recreating")
                        requireActivity().recreate()
                        true
                    }
            }
            getString(R.string.set_key_accent) -> {
                L.d("Configuring accent setting")
                preference.summary = getString(uiSettings.accent.name)
            }
            getString(R.string.set_key_glass_background_image) -> {
                val imageKey = getString(R.string.set_key_glass_background_image)
                val storedUri =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getString(imageKey, null)
                preference.summary =
                    if (storedUri.isNullOrBlank()) {
                        getString(R.string.set_glass_background_image_none)
                    } else {
                        getString(R.string.set_glass_background_image_selected)
                    }
                preference.setOnPreferenceClickListener {
                    glassImagePicker.launch(arrayOf("image/*"))
                    true
                }
            }
            getString(R.string.set_key_glass_background_style) -> {
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, value ->
                        updateGlassPreferenceVisibility(value as? String ?: "preset")
                        true
                    }
                val style =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getString(preference.key, "preset") ?: "preset"
                updateGlassPreferenceVisibility(style)
            }
            getString(R.string.set_key_glass_background_color_one),
            getString(R.string.set_key_glass_background_color_two),
            getString(R.string.set_key_glass_background_color_three),
            getString(R.string.set_key_glass_background_solid_color) -> {
                val defaults =
                    mapOf(
                        getString(R.string.set_key_glass_background_color_one) to "#10172A",
                        getString(R.string.set_key_glass_background_color_two) to "#36527F",
                        getString(R.string.set_key_glass_background_color_three) to "#91578B",
                        getString(R.string.set_key_glass_background_solid_color) to "#10172A",
                    )
                val selectedColor =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getString(preference.key, defaults[preference.key]) ?: "#10172A"
                preference.summary =
                    getString(R.string.set_glass_background_color_summary, selectedColor)
                preference.setOnPreferenceClickListener {
                    showGlassColorPicker(preference)
                    true
                }
            }

            getString(R.string.set_key_black_theme) -> {
                L.d("Configuring black theme setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, _ ->
                        val activity = requireActivity()
                        if (activity.isNight) {
                            L.d("Black theme changed in night mode, recreating")
                            activity.recreate()
                        }

                        true
                    }
            }
        }
    }

    private fun updateGlassPreferenceVisibility(style: String) {
        setGlassPreferenceVisible(R.string.set_key_glass_background_preset, style == "preset")
        val useCustomMix = style == "custom"
        setGlassPreferenceVisible(R.string.set_key_glass_background_color_one, useCustomMix)
        setGlassPreferenceVisible(R.string.set_key_glass_background_color_two, useCustomMix)
        setGlassPreferenceVisible(R.string.set_key_glass_background_color_three, useCustomMix)
        setGlassPreferenceVisible(R.string.set_key_glass_background_image, style == "image")
        setGlassPreferenceVisible(R.string.set_key_glass_background_solid_color, style == "solid")
        val showEffects = style != "off"
        setGlassPreferenceVisible(R.string.set_key_glass_background_blur, showEffects)
        setGlassPreferenceVisible(R.string.set_key_glass_background_darkness, showEffects)
    }

    private fun setGlassPreferenceVisible(keyRes: Int, visible: Boolean) {
        findPreference<Preference>(getString(keyRes))?.isVisible = visible
    }

    private fun showGlassColorPicker(preference: Preference) {
        val entries = resources.getStringArray(R.array.entries_glass_background_colors)
        val colors = resources.getStringArray(R.array.values_glass_background_colors)
        val grid =
            GridLayout(requireContext()).apply {
                columnCount = 2
                useDefaultMargins = true
            }
        val swatches = mutableListOf<Pair<MaterialButton, String>>()
        val density = resources.displayMetrics.density
        val margin = (4 * density).toInt()
        entries.forEachIndexed { index, label ->
            val color = colors.getOrElse(index) { "#10172A" }
            val colorInt = runCatching { Color.parseColor(color) }.getOrDefault(Color.DKGRAY)
            val button =
                MaterialButton(requireContext()).apply {
                    text = "$label\n$color"
                    textSize = 12f
                    maxLines = 2
                    isAllCaps = false
                    setTextColor(
                        if (Color.luminance(colorInt) < 0.45f) Color.WHITE else Color.BLACK
                    )
                    backgroundTintList = ColorStateList.valueOf(colorInt)
                    contentDescription = "$label, $color"
                    insetTop = 0
                    insetBottom = 0
                }
            val params =
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = (56 * density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(margin, margin, margin, margin)
                }
            grid.addView(button, params)
            swatches.add(button to color)
        }
        val scroll =
            ScrollView(requireContext()).apply {
                isFillViewport = true
                addView(
                    grid,
                    ViewGroup.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        val customButton =
            MaterialButton(requireContext()).apply {
                text = getString(R.string.set_glass_custom_color_hex)
                isAllCaps = false
            }
        val content =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    scroll,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (300 * density).toInt(),
                    ),
                )
                addView(
                    customButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.title)
                .setView(content)
                .setNegativeButton(R.string.lbl_cancel, null)
                .create()
        swatches.forEach { (button, color) ->
            button.setOnClickListener {
                saveGlassColor(preference, color)
                dialog.dismiss()
            }
        }
        customButton.setOnClickListener {
            dialog.dismiss()
            showCustomGlassColorDialog(preference)
        }
        dialog.show()
    }

    private fun showCustomGlassColorDialog(preference: Preference) {
        val current =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(preference.key, "#10172A") ?: "#10172A"
        val input =
            EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine(true)
                hint = "#RRGGBB"
                setText(current)
            }
        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.title)
                .setMessage(R.string.set_glass_custom_color_invalid)
                .setView(input)
                .setNegativeButton(R.string.lbl_cancel, null)
                .setPositiveButton(R.string.lbl_save, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                val valid =
                    value.matches(Regex("^#[0-9A-Fa-f]{6}$")) &&
                        runCatching { Color.parseColor(value) }.isSuccess
                if (valid) {
                    saveGlassColor(preference, value.uppercase(Locale.ROOT))
                    dialog.dismiss()
                } else {
                    input.error = getString(R.string.set_glass_custom_color_invalid)
                }
            }
        }
        dialog.show()
    }

    private fun saveGlassColor(preference: Preference, color: String) {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
            putString(preference.key, color)
        }
        preference.summary = getString(R.string.set_glass_background_color_summary, color)
    }
}
