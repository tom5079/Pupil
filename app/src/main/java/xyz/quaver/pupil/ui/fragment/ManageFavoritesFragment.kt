/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

import android.content.Intent
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.snackbar.Snackbar
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import org.kodein.di.direct
import org.kodein.di.instance
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import java.io.IOException
/*
class ManageFavoritesFragment : PreferenceFragmentCompat(), DIAware {

    private lateinit var progressDrawable: CircularProgressDrawable

    override val di by closestDI()

    private val applicationContext: Pupil by instance()
    private val client: HttpClient by instance()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_favorites_preferences, rootKey)

        val context = requireContext()

        val iconSize = context.resources.getDimensionPixelSize(R.dimen.settings_progressbar_icon_size)
        progressDrawable  = object: CircularProgressDrawable(context) {
            override fun getIntrinsicHeight() = iconSize
            override fun getIntrinsicWidth() = iconSize
        }.apply {
            setStyle(CircularProgressDrawable.DEFAULT)
            setColorSchemeColors(ContextCompat.getColor(context, R.color.colorAccent))
        }

        initPreferences()
    }

    private fun initPreferences() {
        findPreference<Preference>("backup")?.setOnPreferenceClickListener { preference ->

            MainScope().launch {
                preference.icon = progressDrawable
                progressDrawable.start()
            }

            CoroutineScope(Dispatchers.IO).launch {
                kotlin.runCatching {
                    requireContext().openFileInput("favorites.json").use { favorites ->
                        val httpResponse: HttpResponse = client.submitForm(
                            url = "http://ix.io/",
                            formParameters = Parameters.build {
                                append("F:1", favorites.bufferedReader().readText())
                            }
                        )

                        if (httpResponse.status.value != 200) throw IOException("Response code ${httpResponse.status.value}")

                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, httpResponse.receive<String>().replace("\n", ""))
                        }.let {
                            applicationContext.startActivity(Intent.createChooser(it, getString(R.string.settings_backup_share)))
                        }
                    }
                }.onSuccess {
                    MainScope().launch {
                        progressDrawable.stop()
                        preference.icon = null
                    }
                }.onFailure {
                    view?.let {
                        Snackbar.make(it, R.string.settings_backup_failed, Snackbar.LENGTH_LONG).show()
                    }
                }
            }

            true
        }
        findPreference<Preference>("restore")?.setOnPreferenceClickListener {
            val editText = EditText(context).apply {
                setText(context.getString(R.string.backup_url), TextView.BufferType.EDITABLE)
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_restore_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        kotlin.runCatching {
                            val url = editText.text.toString()

                            if (!URLUtil.isValidUrl(url)) throw IllegalArgumentException()

                            client.get<Set<String>>(url).also {
                                direct.instance<SavedSourceSet>(tag = "favorites.json").addAll(mapOf("hitomi.la" to it))
                            }
                        }.onSuccess {
                            MainScope().launch {
                                view?.run {
                                    Snackbar.make(this, context.getString(R.string.settings_restore_success, it.size), Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }.onFailure {
                            MainScope().launch {
                                view?.run {
                                    Snackbar.make(this, R.string.settings_restore_failed, Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }.setNegativeButton(android.R.string.cancel) { _, _ ->
                    // Do Nothing
                }.show()

            true
        }
    }

}*/