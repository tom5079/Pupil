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

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.*
import xyz.quaver.io.FileX
import xyz.quaver.io.util.readText
import xyz.quaver.pupil.R
import xyz.quaver.pupil.client
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.get
import xyz.quaver.pupil.util.restore
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

class ManageFavoritesFragment : PreferenceFragmentCompat() {

    private val requestBackupFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }

        val uri = result.data?.data ?: return@registerForActivityResult
        val context = context ?: return@registerForActivityResult
        val view = view ?: return@registerForActivityResult

        val backupData = runCatching {
            FileX(context, uri).readText()?.let { Json.parseToJsonElement(it) }
        }.getOrNull() ?: run{
            Snackbar.make(view, context.getString(R.string.error), Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        val newFavorites = backupData["favorites"]?.let { Json.decodeFromJsonElement<List<Int>>(it) }.orEmpty()
        val newFavoriteTags = backupData["favorite_tags"]?.let { Json.decodeFromJsonElement<List<Tag>>(it) }.orEmpty()

        favorites.addAll(newFavorites)
        favoriteTags.addAll(newFavoriteTags)

        Snackbar.make(view, context.getString(R.string.settings_restore_success, newFavorites.size + newFavoriteTags.size), Snackbar.LENGTH_LONG).show()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_favorites_preferences, rootKey)

        initPreferences()
    }

    private fun initPreferences() {
        val context = context ?: return

        findPreference<Preference>("backup")?.setOnPreferenceClickListener {
            val favorites = runCatching {
                Json.parseToJsonElement(File(ContextCompat.getDataDir(context), "favorites.json").readText())
            }.getOrNull()
            val favoriteTags = kotlin.runCatching {
                Json.parseToJsonElement(File(ContextCompat.getDataDir(context), "favorites_tags.json").readText())
            }.getOrNull()

            val favoriteJson = buildJsonObject {
                favorites?.let {
                    put("favorites", it)
                }
                favoriteTags?.let {
                    put("favorite_tags", it)
                }
            }

            val backupFile = File(context.filesDir, "pupil-backup.json").also {
                it.writeText(favoriteJson.toString())
            }

            Intent(Intent.ACTION_SEND).apply {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", backupFile)
                setDataAndType(uri, "application/json")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, uri)
            }.let {
                context.startActivity(Intent.createChooser(it, getString(R.string.settings_backup_share)))
            }

            true
        }
        findPreference<Preference>("restore")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }

            requestBackupFileLauncher.launch(intent)

            true
        }
    }

}