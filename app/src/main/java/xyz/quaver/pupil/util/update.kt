/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
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

package xyz.quaver.pupil.util

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.*
import ru.noties.markwon.Markwon
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getGalleryBlock
import xyz.quaver.hitomi.getReader
import xyz.quaver.proxy
import xyz.quaver.pupil.BroadcastReciever
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.download.Cache
import xyz.quaver.pupil.util.download.Metadata
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

fun getReleases(url: String) : JsonArray {
    return try {
        URL(url).readText().let {
            Json.decodeFromString(it)
        }
    } catch (e: Exception) {
        JsonArray(emptyList())
    }
}

fun checkUpdate(context: Context, url: String) : JsonObject? {
    val releases = getReleases(url)

    if (releases.isEmpty())
        return null

    return releases.firstOrNull {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("beta", false))
            true
        else
            it.jsonObject["prerelease"]?.jsonPrimitive?.booleanOrNull == false
    }?.let {
        if (it.jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull == BuildConfig.VERSION_NAME)
            null
        else
            it.jsonObject
    }
}

fun getApkUrl(releases: JsonObject) : String? {
    return releases["assets"]?.jsonArray?.firstOrNull {
        Regex("Pupil-v.+\\.apk").matches(it.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "")
    }.let {
        it?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
    }
}

fun checkUpdate(context: Context, force: Boolean = false) {

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val ignoreUpdateUntil = preferences.getLong("ignore_update_until", 0)

    if (!force && ignoreUpdateUntil > System.currentTimeMillis())
        return

    fun extractReleaseNote(update: JsonObject, locale: Locale) : String {
        val markdown = update["body"]!!.jsonPrimitive.content

        val target = when(locale.language) {
            "ko" -> "한국어"
            "ja" -> "日本語"
            else -> "English"
        }

        val releaseNote = Regex("^# Release Note.+$")
        val language = Regex("^## $target$")
        val end = Regex("^#.+$")

        var releaseNoteFlag = false
        var languageFlag = false

        val result = StringBuilder()

        for(line in markdown.lines()) {
            if (releaseNote.matches(line)) {
                releaseNoteFlag = true
                continue
            }

            if (releaseNoteFlag) {
                if (language.matches(line)) {
                    languageFlag = true
                    continue
                }
            }

            if (languageFlag) {
                if (end.matches(line))
                    break

                result.append(line+"\n")
            }
        }

        return context.getString(R.string.update_release_note, update["tag_name"]?.jsonPrimitive?.contentOrNull, result.toString())
    }

    CoroutineScope(Dispatchers.Default).launch {
        val update =
            checkUpdate(context, context.getString(R.string.release_url)) ?: return@launch

        val url = getApkUrl(update) ?: return@launch

        val dialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.update_title)
            val msg = extractReleaseNote(update, Locale.getDefault())
            setMessage(Markwon.create(context).toMarkdown(msg))
            setPositiveButton(android.R.string.yes) { _, _ ->

                val preference = PreferenceManager.getDefaultSharedPreferences(context)

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                //Cancel any download queued before

                val id = preference.getLong("update_download_id", -1)

                if (id != -1L)
                    downloadManager.remove(id)

                val target = File(context.getExternalFilesDir(null), "Pupil.apk").also {
                    it.delete()
                }

                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(context.getText(R.string.update_notification_description))
                    .setDestinationUri(Uri.fromFile(target))

                downloadManager.enqueue(request).also {
                    preference.edit().putLong("update_download_id", it).apply()
                }
            }
            setNegativeButton(if (force) android.R.string.no else R.string.ignore_update) { _, _ ->
                if (!force)
                    preferences.edit()
                        .putLong("ignore_update_until", System.currentTimeMillis() + 604800000)
                        .apply()
            }
        }

        launch(Dispatchers.Main) {
            dialog.show()
        }
    }
}