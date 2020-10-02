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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.quaver.io.FileX
import xyz.quaver.io.util.deleteRecursively
import xyz.quaver.pupil.R
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.util.byteToString
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import java.io.File

class ManageStorageFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {

    private var job: Job? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_storage_preferences, rootKey)

        initPreferences()
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        val context = context ?: return false

        with(preference) {
            this ?: return false

            when (key) {
                "delete_cache" -> {
                    val dir = File(context.cacheDir, "imageCache")

                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_cache_alert_message)
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            if (dir.exists())
                                dir.deleteRecursively()

                            Cache.instances.clear()

                            summary = context.getString(R.string.settings_storage_usage, byteToString(0))
                            CoroutineScope(Dispatchers.IO).launch {
                                var size = 0L

                                dir.walk().forEach {
                                    size += it.length()

                                    launch(Dispatchers.Main) {
                                        summary = context.getString(R.string.settings_storage_usage, byteToString(size))
                                    }
                                }
                            }
                        }
                        setNegativeButton(android.R.string.cancel) { _, _ -> }
                    }.show()
                }
                "delete_downloads" -> {
                    val dir = DownloadManager.getInstance(context).downloadFolder

                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_downloads_alert_message)
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            CoroutineScope(Dispatchers.IO).launch {
                                job?.cancel()
                                launch(Dispatchers.Main) {
                                    summary = context.getString(R.string.settings_storage_usage_loading)
                                }

                                if (dir.exists())
                                    dir.listFiles()?.forEach { (it as? FileX)?.deleteRecursively() }

                                job = launch {
                                    var size = 0L

                                    launch(Dispatchers.Main) {
                                        summary = context.getString(R.string.settings_storage_usage, byteToString(size))
                                    }
                                    dir.walk().forEach {
                                        size += it.length()

                                        launch(Dispatchers.Main) {
                                            summary = context.getString(R.string.settings_storage_usage, byteToString(size))
                                        }
                                    }
                                }
                            }
                        }
                        setNegativeButton(android.R.string.cancel) { _, _ -> }
                    }.show()
                }
                "clear_history" -> {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.warning)
                        setMessage(R.string.settings_clear_history_alert_message)
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            histories.clear()
                            summary = context.getString(R.string.settings_clear_history_summary, histories.size)
                        }
                        setNegativeButton(android.R.string.cancel) { _, _ -> }
                    }.show()
                }
                else -> return false
            }
        }

        return true
    }

    private fun initPreferences() {
        val context = context ?: return

        with(findPreference<Preference>("delete_cache")) {
            this ?: return@with

            val dir = File(context.cacheDir, "imageCache")

            summary = context.getString(R.string.settings_storage_usage, byteToString(0))
            CoroutineScope(Dispatchers.IO).launch {
                var size = 0L

                dir.walk().forEach {
                    size += it.length()

                    launch(Dispatchers.Main) {
                        summary = context.getString(R.string.settings_storage_usage, byteToString(size))
                    }
                }
            }

            onPreferenceClickListener = this@ManageStorageFragment
        }

        with(findPreference<Preference>("delete_downloads")) {
            this ?: return@with

            val dir = DownloadManager.getInstance(context).downloadFolder

            summary = context.getString(R.string.settings_storage_usage, byteToString(0))
            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                var size = 0L

                dir.walk().forEach {
                    launch(Dispatchers.Main) {
                        summary = context.getString(R.string.settings_storage_usage, byteToString(size))
                    }

                    size += it.length()
                }
            }

            onPreferenceClickListener = this@ManageStorageFragment
        }

        with(findPreference<Preference>("clear_history")) {
            this ?: return@with

            summary = context.getString(R.string.settings_clear_history_summary, histories.size)

            onPreferenceClickListener = this@ManageStorageFragment
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

}