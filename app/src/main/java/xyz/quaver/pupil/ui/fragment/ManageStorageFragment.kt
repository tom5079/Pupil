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

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.getDownloadDirectory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ManageStorageFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_storage_preferences, rootKey)

        initPreferences()
    }

    private fun getDirSize(dir: File) : String {
        return context?.getString(R.string.settings_storage_usage,
            Runtime.getRuntime().exec("du -hs " + dir.canonicalPath).let {
                BufferedReader(InputStreamReader(it.inputStream)).use { reader ->
                    reader.readLine()?.split('\t')?.firstOrNull() ?: "0"
                }
            }
        ) ?: ""
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        with(preference) {
            this ?: return false

            when (key) {
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
                    val dir = DownloadManager.getInstance(context).downloadFolder

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
                else -> return false
            }
        }

        return true
    }

    private fun initPreferences() {
        with(findPreference<Preference>("delete_cache")) {
            this ?: return@with

            val dir = File(requireContext().cacheDir, "imageCache")

            summary = getString(R.string.settings_storage_usage_loading)
            CoroutineScope(Dispatchers.IO).launch {
                getDirSize(dir).let {
                    launch(Dispatchers.Main) {
                        this@with.summary = it
                    }
                }
            }

            onPreferenceClickListener = this@ManageStorageFragment
        }

        with(findPreference<Preference>("delete_downloads")) {
            this ?: return@with

            val dir = DownloadManager.getInstance(context).downloadFolder

            summary = getString(R.string.settings_storage_usage_loading)
            CoroutineScope(Dispatchers.IO).launch {
                getDirSize(dir).let {
                    launch(Dispatchers.Main) {
                        this@with.summary = it
                    }
                }
            }

            onPreferenceClickListener = this@ManageStorageFragment
        }

        with(findPreference<Preference>("clear_history")) {
            this ?: return@with

            summary = getString(R.string.settings_clear_history_summary, histories.size)

            onPreferenceClickListener = this@ManageStorageFragment
        }
    }

}