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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.internal.EnumSerializer
import kotlinx.serialization.json.*
import ru.noties.markwon.Markwon
import xyz.quaver.availableInHiyobi
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import java.io.File
import java.net.URL
import java.util.*

fun getReleases(url: String) : JsonArray {
    return try {
        URL(url).readText().let {
            Json(JsonConfiguration.Stable).parse(JsonArray.serializer(), it)
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
            it.jsonObject["prerelease"]?.boolean == false
    }?.let {
        if (it.jsonObject["tag_name"]?.content == BuildConfig.VERSION_NAME)
            null
        else
            it.jsonObject
    }
}

fun getApkUrl(releases: JsonObject) : String? {
    return releases["assets"]?.jsonArray?.firstOrNull {
        Regex("Pupil-v.+\\.apk").matches(it.jsonObject["name"]?.content ?: "")
    }.let {
        it?.jsonObject?.get("browser_download_url")?.content
    }
}

const val UPDATE_NOTIFICATION_ID = 384823
fun checkUpdate(context: AppCompatActivity, force: Boolean = false) {

    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val ignoreUpdateUntil = preferences.getLong("ignore_update_until", 0)

    if (!force && ignoreUpdateUntil > System.currentTimeMillis())
        return

    fun extractReleaseNote(update: JsonObject, locale: Locale) : String {
        val markdown = update["body"]!!.content

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

        return context.getString(R.string.update_release_note, update["tag_name"]?.content, result.toString())
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

                val notificationManager = NotificationManagerCompat.from(context)
                val builder = NotificationCompat.Builder(context, "download").apply {
                    setContentTitle(context.getString(R.string.update_notification_description))
                    setSmallIcon(android.R.drawable.stat_sys_download)
                    priority = NotificationCompat.PRIORITY_LOW
                }

                CoroutineScope(Dispatchers.IO).launch io@{
                    val target = File(getDownloadDirectory(context), "Pupil.apk")

                    try {
                        URL(url).download(target) { progress, fileSize ->
                            builder.setProgress(fileSize.toInt(), progress.toInt(), false)
                            notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
                        }
                    } catch (e: Exception) {
                        builder.apply {
                            setContentText(context.getString(R.string.update_failed))
                            setMessage(context.getString(R.string.update_failed_message))
                            setSmallIcon(android.R.drawable.stat_sys_download_done)
                        }

                        notificationManager.cancel(UPDATE_NOTIFICATION_ID)
                        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())

                        return@io
                    }

                    val install = Intent(Intent.ACTION_VIEW).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        setDataAndType(FileProvider.getUriForFile(
                            context,
                            context.applicationContext.packageName + ".fileprovider",
                            target
                        ), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"))

                        if (resolveActivity(context.packageManager) == null)
                            setDataAndType(Uri.fromFile(target),
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"))
                    }

                    builder.apply {
                        setContentIntent(PendingIntent.getActivity(context, 0, install, 0))
                        setProgress(0, 0, false)
                        setSmallIcon(android.R.drawable.stat_sys_download_done)
                        setContentTitle(context.getString(R.string.update_download_completed))
                        setContentText(context.getString(R.string.update_download_completed_description))
                    }

                    notificationManager.cancel(UPDATE_NOTIFICATION_ID)

                    if (context.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                        context.startActivity(install)
                    else
                        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build())
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

fun getOldReaderGalleries(context: Context) : List<File> {
    val oldGallery = mutableListOf<File>()

    listOf(
        getDownloadDirectory(context),
        File(context.cacheDir, "imageCache")
    ).forEach { root ->
        root.listFiles()?.forEach { gallery ->
            File(gallery, "reader.json").let { readerFile ->
                if (!readerFile.exists())
                    return@let

                try {
                    Json(JsonConfiguration.Stable).parseJson(readerFile.readText())
                        .jsonObject.let { reader ->
                        if (!reader.contains("code"))
                            oldGallery.add(gallery)
                    }
                } catch (e: Exception) {
                    // do nothing
                }
            }
        }
    }

    return oldGallery
}

@UseExperimental(InternalSerializationApi::class)
fun updateOldReaderGalleries(context: Context) {

    val json = Json(JsonConfiguration.Stable)

   getOldReaderGalleries(context).forEach { gallery ->
       val reader = json.parseJson(File(gallery, "reader.json").apply {
           if (!exists())
               return@forEach
       }.readText())
           .jsonObject.toMutableMap()

       val codeSerializer = EnumSerializer(Reader.Code::class)

       reader["code"] = when {
           (File(gallery, "images").list()?.
               all { !it.endsWith("webp") } ?: return@forEach) &&
                   availableInHiyobi(gallery.name.toIntOrNull() ?: return@forEach)
                -> json.toJson(codeSerializer, Reader.Code.HIYOBI)
           else -> json.toJson(codeSerializer, Reader.Code.HITOMI)
       }

       File(gallery, "reader.json").writeText(JsonObject(reader).toString())
   }

}