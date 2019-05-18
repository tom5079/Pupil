package xyz.quaver.pupil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import android.util.SparseArray
import com.finotes.android.finotescore.Fn
import com.finotes.android.finotescore.ObservableApplication
import com.finotes.android.finotescore.Severity
import kotlinx.coroutines.Job

class Pupil : ObservableApplication() {

    override fun onCreate() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)

        super.onCreate()
        Fn.init(this)

        Fn.enableFrameDetection()

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