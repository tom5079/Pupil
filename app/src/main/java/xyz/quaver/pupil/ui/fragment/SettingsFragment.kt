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

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.rdrei.android.dirchooser.DirectoryChooserActivity
import net.rdrei.android.dirchooser.DirectoryChooserConfig
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.ui.SettingsActivity
import xyz.quaver.pupil.ui.dialog.DefaultQueryDialog
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialog
import xyz.quaver.pupil.ui.dialog.MirrorDialog
import xyz.quaver.pupil.ui.dialog.ProxyDialog
import xyz.quaver.pupil.util.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


class SettingsFragment :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    lateinit var sharedPreference: SharedPreferences

    override fun onResume() {
        super.onResume()

        val lockManager = LockManager(requireContext())

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

    private fun getDirSize(dir: File) : String {
        return context?.getString(R.string.settings_storage_usage,
            Runtime.getRuntime().exec("du -hs " + dir.absolutePath).let {
                BufferedReader(InputStreamReader(it.inputStream)).use { reader ->
                    reader.readLine()?.split('\t')?.firstOrNull() ?: "0"
                }
            }
        ) ?: ""
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "app_version" -> {
                    checkUpdate(activity as SettingsActivity, true)
                }
                "delete_cache" -> {
                    val dir = File(requireContext().cacheDir, "imageCache")

                    AlertDialog.Builder(requireContext()).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_cache_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getString(R.string.settings_storage_usage_loading)

                            CoroutineScope(Dispatchers.IO).launch {
                                getDirSize(dir).let {
                                    launch(Dispatchers.Main) {
                                        this@with.summary = it
                                    }
                                }
                            }
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                }
                "delete_downloads" -> {
                    val dir = getDownloadDirectory(requireContext())

                    AlertDialog.Builder(requireContext()).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_downloads_alert_message)
                        setPositiveButton(android.R.string.yes) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            summary = getString(R.string.settings_storage_usage_loading)

                            CoroutineScope(Dispatchers.IO).launch {
                                getDirSize(dir).let {
                                    launch(Dispatchers.Main) {
                                        this@with.summary = it
                                    }
                                }
                            }
                        }
                        setNegativeButton(android.R.string.no) { _, _ -> }
                    }.show()
                }
                "clear_history" -> {
                    val histories = (requireContext().applicationContext as Pupil).histories

                    AlertDialog.Builder(requireContext()).apply {
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
                    DownloadLocationDialog(requireActivity()).show()
                }
                "default_query" -> {
                    DefaultQueryDialog(requireContext()).apply {
                        onPositiveButtonClickListener = { newTags ->
                            sharedPreferences.edit().putString("default_query", newTags.toString()).apply()
                            summary = newTags.toString()
                        }
                    }.show()
                }
                "app_lock" -> {
                    val intent = Intent(requireContext(), LockActivity::class.java)
                    activity?.startActivityForResult(intent, R.id.request_lock.normalizeID())
                }
                "mirrors" -> {
                    MirrorDialog(requireContext())
                        .show()
                }
                "proxy" -> {
                    ProxyDialog(requireContext())
                        .show()
                }
                "nomedia" -> {
                    File(getDownloadDirectory(context), ".nomedia").createNewFile()
                }
                "user_id" -> {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                        ClipData.newPlainText("user_id", sharedPreference.getString("user_id", ""))
                    )
                    Toast.makeText(context, R.string.settings_user_id_toast, Toast.LENGTH_SHORT).show()
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
        key ?: return

        with(findPreference<Preference>(key)) {
            this ?: return

            when (key) {
                "proxy" -> {
                    summary = getProxyInfo(requireContext()).type.name
                }
                "dl_location" -> {
                    summary = getDownloadDirectory(requireContext()).canonicalPath
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        sharedPreference = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreference.registerOnSharedPreferenceChangeListener(this)

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
                            val manager = requireContext().packageManager
                            val info = manager.getPackageInfo(requireContext().packageName, 0)
                            summary = requireContext().getString(R.string.settings_app_version_description, info.versionName)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "delete_cache" -> {
                            val dir = File(requireContext().cacheDir, "imageCache")

                            summary = getString(R.string.settings_storage_usage_loading)
                            CoroutineScope(Dispatchers.IO).launch {
                                getDirSize(dir).let {
                                    launch(Dispatchers.Main) {
                                        this@with.summary = it
                                    }
                                }
                            }

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "delete_downloads" -> {
                            val dir = getDownloadDirectory(requireContext())

                            summary = getString(R.string.settings_storage_usage_loading)
                            CoroutineScope(Dispatchers.IO).launch {
                                getDirSize(dir).let {
                                    launch(Dispatchers.Main) {
                                        this@with.summary = it
                                    }
                                }
                            }

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "clear_history" -> {
                            val histories = (requireActivity().application as Pupil).histories
                            summary = getString(R.string.settings_clear_history_summary, histories.size)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "dl_location" -> {
                            summary = getDownloadDirectory(requireContext()).canonicalPath

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "default_query" -> {
                            summary = sharedPreference.getString("default_query", "") ?: ""

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "app_lock" -> {
                            val lockManager = LockManager(requireContext())
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
                        "proxy" -> {
                            summary = getProxyInfo(requireContext()).type.name

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "dark_mode" -> {
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                        "nomedia" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "old_import_galleries" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "user_id" -> {
                            summary = sharedPreference.getString("user_id", "")
                            onPreferenceClickListener = this@SettingsFragment
                        }
                    }

                }
            }
        }
    }
}