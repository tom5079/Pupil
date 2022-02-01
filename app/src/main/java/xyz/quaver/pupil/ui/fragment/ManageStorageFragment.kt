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

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.quaver.io.FileX
import xyz.quaver.io.SAFileX
import xyz.quaver.io.util.deleteRecursively
import xyz.quaver.io.util.getChild
import xyz.quaver.io.util.readText
import xyz.quaver.io.util.writeText
import xyz.quaver.pupil.R
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.hitomi.json
import xyz.quaver.pupil.util.byteToString
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.downloader.Metadata
import java.io.File
import kotlin.math.roundToInt

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
                "recover_downloads" -> {
                    val density = context.resources.displayMetrics.density
                    this.icon = object: CircularProgressDrawable(context) {
                        override fun getIntrinsicHeight() = (24*density).roundToInt()
                        override fun getIntrinsicWidth() = (24*density).roundToInt()
                    }.apply {
                        setStyle(CircularProgressDrawable.DEFAULT)
                        colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                        start()
                    }

                    val downloadManager = DownloadManager.getInstance(context)

                    val downloadFolderMap = downloadManager.downloadFolderMap

                    downloadFolderMap.clear()

                    downloadManager.downloadFolder.listFiles { file -> file.isDirectory }?.forEach { folder ->
                        val metadataFile = FileX(context, folder, ".metadata")

                        if (!metadataFile.exists()) return@forEach

                        val metadata = metadataFile.readText()?.let {
                            json.decodeFromString<Metadata>(it)
                        } ?: return@forEach

                        val galleryID = metadata.galleryBlock?.id ?: metadata.galleryInfo?.id?.toIntOrNull() ?: return@forEach

                        downloadFolderMap[galleryID] = folder.name
                    }

                    downloadManager.downloadFolderMap.putAll(downloadFolderMap)
                    val downloads = FileX(context, downloadManager.downloadFolder, ".download")

                    if (!downloads.exists()) downloads.createNewFile()
                    downloads.writeText(Json.encodeToString(downloadFolderMap))

                    this.icon = null
                    Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
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
                                    dir.listFiles()?.forEach {
                                        when (it) {
                                            is FileX -> it.deleteRecursively()
                                            else -> it.deleteRecursively()
                                        }
                                    }

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

        with(findPreference<Preference>("recover_downloads")) {
            this ?: return@with

            onPreferenceClickListener = this@ManageStorageFragment
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

}