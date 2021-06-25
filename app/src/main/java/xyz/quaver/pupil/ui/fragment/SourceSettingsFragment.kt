/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import xyz.quaver.pupil.sources.SourcePreferenceIDs
import xyz.quaver.pupil.ui.dialog.DefaultQueryDialogFragment
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.getAvailableLanguages
import xyz.quaver.pupil.util.updateTranslations
import java.util.*

class SourceSettingsFragment(private val source: String) :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener,
    DIAware {
    override val di by closestDI()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(direct.instance<SourcePreferenceIDs>().toMap()[source]!!, rootKey)

        initPreferences()
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "hitomi.default_query" -> {
                    DefaultQueryDialogFragment().apply {
                        onPositiveButtonClickListener = { newTags ->
                            Preferences["hitomi.default_query"] = newTags.toString()
                            summary = newTags.toString()
                        }
                    }.show(parentFragmentManager, "Default Query Dialog")
                }
            }
        }

        return true
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "hitomi.tag_translation" -> {
                    updateTranslations()
                }
                else -> return false
            }
        }

        return true
    }

    private fun initPreferences() {
        for (i in 0 until preferenceScreen.preferenceCount) {

            preferenceScreen.getPreference(i).run {
                if (this is PreferenceCategory)
                    (0 until preferenceCount).map { getPreference(it) }
                else
                    listOf(this)
            }.forEach { preference ->
                with (preference) {
                    when (key) {
                        "hitomi.default_query" -> {
                            summary = Preferences.get<String>(key)

                            onPreferenceClickListener = this@SourceSettingsFragment
                        }

                        "hitomi.tag_translation" -> {
                            this as ListPreference

                            isEnabled = false

                            CoroutineScope(Dispatchers.IO).launch {
                                kotlin.runCatching {
                                    val languages = getAvailableLanguages().distinct().toTypedArray()

                                    entries = languages.map { Locale(it).let { loc -> loc.getDisplayLanguage(loc) } }.toTypedArray()
                                    entryValues = languages

                                    launch(Dispatchers.Main) {
                                        isEnabled = true
                                    }
                                }
                            }

                            onPreferenceChangeListener = this@SourceSettingsFragment

                        }
                    }
                }
            }
        }
    }
}