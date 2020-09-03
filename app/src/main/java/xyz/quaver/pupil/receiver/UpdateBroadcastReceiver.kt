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

package xyz.quaver.pupil.receiver

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.Preferences
import java.io.File

class UpdateBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return

        when (intent?.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {

                // Validate download
                val downloadID: Long = Preferences["update_download_id"]
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2) != downloadID)
                    return

                // Get target uri

                val query = DownloadManager.Query()
                    .setFilterById(downloadID)

                val uri = downloadManager.query(query).use { cursor ->
                    cursor.moveToFirst()

                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)).let {
                        val uri = Uri.parse(it)

                        when (uri.scheme) {
                            "file" ->
                                FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", File(uri.path!!))
                            "content" -> uri
                            else -> return
                        }
                    }
                }

                // Build Notification

                val notificationManager = NotificationManagerCompat.from(context)

                val pendingIntent = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"))
                }, 0)

                val notification = NotificationCompat.Builder(context, "update")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(context.getText(R.string.update_download_completed))
                    .setContentText(context.getText(R.string.update_download_completed_description))
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager.notify(R.id.notification_id_update, notification)
            }
        }
    }

}