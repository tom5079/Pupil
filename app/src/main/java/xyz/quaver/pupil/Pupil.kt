package xyz.quaver.pupil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import xyz.quaver.pupil.util.Histories
import java.io.File

class Pupil : MultiDexApplication() {

    lateinit var histories: Histories
    lateinit var downloads: Histories
    lateinit var favorites: Histories

    init {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    override fun onCreate() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)

        histories = Histories(File(ContextCompat.getDataDir(this), "histories.json"))
        downloads = Histories(File(ContextCompat.getDataDir(this), "downloads.json"))
        favorites = Histories(File(ContextCompat.getDataDir(this), "favorites.json"))

        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

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

        super.onCreate()
    }

}