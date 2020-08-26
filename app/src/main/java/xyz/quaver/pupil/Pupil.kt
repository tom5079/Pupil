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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import xyz.quaver.proxy
import xyz.quaver.pupil.util.GalleryList
import xyz.quaver.pupil.util.getProxy
import java.io.File
import java.util.*

class Pupil : MultiDexApplication() {

    lateinit var histories: GalleryList
    lateinit var favorites: GalleryList

    init {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    override fun onCreate() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)

        val userID =
            if (preference.getString("user_id", "").isNullOrEmpty()) {
                UUID.randomUUID().toString().also {
                    preference.edit().putString("user_id", it).apply()
                }
            } else
                preference.getString("user_id", "") ?: ""

        FirebaseCrashlytics.getInstance().setUserId(userID)

        proxy = getProxy(this)

        try {
            preference.getString("dl_location", null).also {
                if (!File(it!!).canWrite())
                    throw Exception()
            }
        } catch (e: Exception) {
            preference.edit().remove("dl_location").apply()
        }

        histories = GalleryList(File(ContextCompat.getDataDir(this), "histories.json"))
        favorites = GalleryList(File(ContextCompat.getDataDir(this), "favorites.json"))

        if (BuildConfig.DEBUG)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)

        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(NotificationChannel("download", getString(R.string.channel_download), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_download_description)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            })

            manager.createNotificationChannel(NotificationChannel("update", getString(R.string.channel_update), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_update_description)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            })

            manager.createNotificationChannel(NotificationChannel("import", getString(R.string.channel_update), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_update_description)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            })
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        AppCompatDelegate.setDefaultNightMode(when (preference.getBoolean("dark_mode", false)) {
            true -> AppCompatDelegate.MODE_NIGHT_YES
            false -> AppCompatDelegate.MODE_NIGHT_NO
        })

        super.onCreate()
    }

}