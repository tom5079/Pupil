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
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.ui.SettingsActivity
import xyz.quaver.pupil.ui.dialog.DefaultQueryDialog
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialog
import xyz.quaver.pupil.ui.dialog.MirrorDialog
import xyz.quaver.pupil.util.*
import java.io.File


class SettingsFragment :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        val lockManager = LockManager(context!!)

        findPreference<Preference>("app_lock")?.summary = if (lockManager.locks.isNullOrEmpty()) {
            getString(R.string.settings_lock_none)
        } else {
            lockManager.locks?.joinToString(", ") {
                when(it.type) {
                    Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                    Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                    Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                }
            }
        }
    }

    private fun getDirSize(dir: DocumentFile) : String {
        val size = dir.walk().map { it.length() }.sum()

        return getString(R.string.settings_clear_summary, byteToString(size))
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "app_version" -> {
                    checkUpdate(activity as SettingsActivity, true)
                }
                "delete_cache" -> {
                    val dir = DocumentFile.fromFile(File(context.cacheDir, "imageCache"))

                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_cache_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getDirSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                }
                "delete_downloads" -> {
                    val dir = getDownloadDirectory(context)!!

                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_downloads_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getDirSize(dir)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                }
                "clear_history" -> {
                    val histories = (context.applicationContext as Pupil).histories

                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_history_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            histories.clear()
                            summary = getString(R.string.settings_clear_history_summary, histories.size)
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                }
                "dl_location" -> {
                    DownloadLocationDialog(activity!!).show()
                }
                "default_query" -> {
                    DefaultQueryDialog(context).apply {
                        onPositiveButtonClickListener = { newTags ->
                            sharedPreferences.edit().putString("default_query", newTags.toString()).apply()
                            summary = newTags.toString()
                        }
                    }.show()
                }
                "app_lock" -> {
                    val intent = Intent(context, LockActivity::class.java)
                    activity?.startActivityForResult(intent, REQUEST_LOCK)
                }
                "mirrors" -> {
                    MirrorDialog(context)
                        .show()
                }
                "backup" -> {
                    File(ContextCompat.getDataDir(context), "favorites.json").copyTo(
                        context,
                        getDownloadDirectory(context)?.createFile("null", "favorites.json")!!
                    )

                    Snackbar.make(this@SettingsFragment.listView, R.string.settings_backup_snackbar, Snackbar.LENGTH_LONG)
                        .show()
                }
                "restore" -> {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }

                    activity?.startActivityForResult(intent, REQUEST_RESTORE)
                }
                else -> return false
            }
        }

        return true
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "dark_mode" -> {
                    AppCompatDelegate.setDefaultNightMode(when (newValue as Boolean) {
                        true -> AppCompatDelegate.MODE_NIGHT_YES
                        false -> AppCompatDelegate.MODE_NIGHT_NO
                    })
                }
                else -> return false
            }
        }

        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "dl_location" -> {
                findPreference<Preference>(key)?.summary =
                    FileUtils.getPath(context, getDownloadDirectory(context!!)?.uri)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        initPreferences()
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
                        "app_version" -> {
                            val manager = context.packageManager
                            val info = manager.getPackageInfo(context.packageName, 0)
                            summary = context.getString(R.string.settings_app_version_description, info.versionName)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "delete_cache" -> {
                            val dir = DocumentFile.fromFile(File(context.cacheDir, "imageCache"))
                            summary = getDirSize(dir)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "delete_downloads" -> {
                            val dir = getDownloadDirectory(context)!!
                            summary = getDirSize(dir)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "clear_history" -> {
                            val histories = (activity!!.application as Pupil).histories
                            summary = getString(R.string.settings_clear_history_summary, histories.size)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "dl_location" -> {
                            summary = FileUtils.getPath(context, getDownloadDirectory(context)?.uri)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "default_query" -> {
                            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                            summary = preferences.getString("default_query", "") ?: ""

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "app_lock" -> {
                            val lockManager = LockManager(context)
                            summary =
                                if (lockManager.locks.isNullOrEmpty()) {
                                    getString(R.string.settings_lock_none)
                                } else {
                                    lockManager.locks?.joinToString(", ") {
                                        when (it.type) {
                                            Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                                            Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                                            Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                                        }
                                    }
                                }

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "mirrors" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "dark_mode" -> {
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                        "backup" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "restore" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                    }

                }
            }
        }
    }
}