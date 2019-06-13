package xyz.quaver.pupil

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import xyz.quaver.pupil.util.Histories
import java.io.File

class Pupil : Application() {

    lateinit var histories: Histories
    lateinit var downloads: Histories
    lateinit var favorites: Histories

    override fun onCreate() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)

        histories = Histories(File(ContextCompat.getDataDir(this), "histories.json"))
        downloads = Histories(File(ContextCompat.getDataDir(this), "downloads.json"))
        favorites = Histories(File(ContextCompat.getDataDir(this), "favorites.json"))

        super.onCreate()

        if (!preference.getBoolean("channel_created", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel("download", getString(R.string.channel_download), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.channel_download_description)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                manager.createNotificationChannel(channel)
            }

            preference.edit().putBoolean("channel_created", true).apply()
        }
    }

}