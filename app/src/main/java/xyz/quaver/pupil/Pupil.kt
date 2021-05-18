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

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.fresco.FrescoImageLoader
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import okhttp3.*
import org.kodein.di.*
import org.kodein.di.android.x.androidXModule
import xyz.quaver.io.FileX
import xyz.quaver.pupil.sources.sourceModule
import xyz.quaver.pupil.util.*
import xyz.quaver.setClient
import java.io.File
import java.util.*

lateinit var clientBuilder: OkHttpClient.Builder

var clientHolder: OkHttpClient? = null
val client: OkHttpClient
    get() = clientHolder ?: clientBuilder.build().also {
        clientHolder = it
        setClient(it)
    }

class Pupil : Application(), DIAware {

    override val di: DI by DI.lazy {
        import(androidXModule(this@Pupil))
        import(sourceModule)

        bind { provider { client } }
        bind { singleton { ImageCache(this@Pupil) } }
        bind { singleton { DownloadManager(this@Pupil) } }

        bind<SavedSourceSet>(tag = "histories") with singleton { SavedSourceSet(File(ContextCompat.getDataDir(this@Pupil), "histories.json")) }
        bind<SavedSourceSet>(tag = "favorites") with singleton { SavedSourceSet(File(ContextCompat.getDataDir(this@Pupil), "favorites.json")) }
        bind<SavedSourceSet>(tag = "favoriteTags") with singleton { SavedSourceSet(File(ContextCompat.getDataDir(this@Pupil), "favoriteTags.json")) }
        bind<SavedSourceSet>(tag = "searchHistory") with singleton { SavedSourceSet(File(ContextCompat.getDataDir(this@Pupil), "searchHistory.json")) }
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val userID = Preferences["user_id", ""].let {  userID ->
            if (userID.isEmpty()) UUID.randomUUID().toString().also { Preferences["user_id"] = it }
            else userID
        }

        firebaseAnalytics = Firebase.analytics
        FirebaseCrashlytics.getInstance().setUserId(userID)

        Logger.addLogAdapter(AndroidLogAdapter())

        val proxyInfo = getProxyInfo()

        clientBuilder = OkHttpClient.Builder()
            .proxyInfo(proxyInfo)

        try {
            Preferences.get<String>("download_folder").also {
                if (it.startsWith("content"))
                    contentResolver.takePersistableUriPermission(
                        Uri.parse(it),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                if (!FileX(this, it).canWrite())
                    throw Exception()
            }
        } catch (e: Exception) {
            Preferences.remove("download_folder")
        }

        if (!Preferences["reset_secure", false]) {
            Preferences["security_mode"] = false
            Preferences["reset_secure"] = true
        }

        if (BuildConfig.DEBUG)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)

        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        BigImageViewer.initialize(FrescoImageLoader.with(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(NotificationChannel("download", getString(R.string.channel_download), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_download_description)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            })

            manager.createNotificationChannel(NotificationChannel("downloader", getString(R.string.channel_downloader), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_downloader_description)
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

            manager.createNotificationChannel(NotificationChannel("import", getString(R.string.channel_update), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_update_description)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            })
        }

        AppCompatDelegate.setDefaultNightMode(when (Preferences.get<Boolean>("dark_mode")) {
            true -> AppCompatDelegate.MODE_NIGHT_YES
            false -> AppCompatDelegate.MODE_NIGHT_NO
        })

        super.onCreate()
    }

}