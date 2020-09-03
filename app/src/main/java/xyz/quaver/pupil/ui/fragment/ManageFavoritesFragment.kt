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
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.client
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.util.restore
import java.io.File
import java.io.IOException

class ManageFavoritesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_favorites_preferences, rootKey)

        initPreferences()
    }

    private fun initPreferences() {
        val context = context ?: return

        findPreference<Preference>("backup")?.setOnPreferenceClickListener {
            val request = Request.Builder()
                .url(getString(R.string.backup_url))
                .post(
                    FormBody.Builder()
                        .add("f:1", File(ContextCompat.getDataDir(context), "favorites.json").readText())
                        .build()
                ).build()

            client.newCall(request).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val view = view ?: return
                    Snackbar.make(view, R.string.settings_backup_failed, Snackbar.LENGTH_LONG).show()
                }

                override fun onResponse(call: Call, response: Response) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, response.body()?.use { it.string() }?.replace("\n", ""))
                    }.let {
                        context.startActivity(Intent.createChooser(it, getString(R.string.settings_backup_share)))
                    }
                }
            })

            true
        }
        findPreference<Preference>("restore")?.setOnPreferenceClickListener {
            val editText = EditText(context).apply {
                setText(getString(R.string.backup_url), TextView.BufferType.EDITABLE)
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.settings_restore_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    restore(favorites, editText.text.toString(),
                        onFailure = onFailure@{
                            val view = view ?: return@onFailure
                            Snackbar.make(view, R.string.settings_restore_failed, Snackbar.LENGTH_LONG).show()
                        }, onSuccess = onSuccess@{
                            val view = view ?: return@onSuccess
                            Snackbar.make(view, getString(R.string.settings_restore_success, it.size), Snackbar.LENGTH_LONG).show()
                        })
                }.setNegativeButton(android.R.string.cancel) { _, _ ->
                    // Do Nothing
                }.show()

            true
        }
    }

}