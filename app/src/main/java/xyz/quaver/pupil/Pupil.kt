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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import xyz.quaver.io.FileX
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.*
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.setClient
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

typealias PupilInterceptor = (Interceptor.Chain) -> Response

lateinit var histories: SavedSet<Int>
    private set
lateinit var favorites: SavedSet<Int>
    private set
lateinit var favoriteTags: SavedSet<Tag>
    private set
lateinit var searchHistory: SavedSet<String>
    private set

val interceptors = mutableMapOf<KClass<out Any>, PupilInterceptor>()

lateinit var clientBuilder: OkHttpClient.Builder

var clientHolder: OkHttpClient? = null
val client: OkHttpClient
    get() = clientHolder ?: clientBuilder.build().also {
        clientHolder = it
        setClient(it)
    }

class Pupil : Application() {

    override fun onCreate() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val userID = Preferences["user_id", ""].let {  userID ->
            if (userID.isEmpty()) UUID.randomUUID().toString().also { Preferences["user_id"] = it }
            else userID
        }

        FirebaseCrashlytics.getInstance().setUserId(userID)

        val proxyInfo = getProxyInfo()

        clientBuilder = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .proxyInfo(proxyInfo)
            .addInterceptor { chain ->
                val request = chain.request()
                val tag = request.tag() ?: return@addInterceptor chain.proceed(request)

                interceptors[tag::class]?.invoke(chain) ?: chain.proceed(request)
            }

        try {
            Preferences.get<String>("download_folder").also {
                if (it.startsWith("content") && Build.VERSION.SDK_INT > 19)
                    contentResolver.takePersistableUriPermission(
                        Uri.parse(it),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                if (!FileX(this, it).canWrite())
                    throw Exception()

                DownloadManager.getInstance(this).migrate()
            }
        } catch (e: Exception) {
            Preferences.remove("download_folder")
        }

        if (!Preferences["reset_secure", false]) {
            Preferences["security_mode"] = false
            Preferences["reset_secure"] = true
        }

        histories = SavedSet(File(ContextCompat.getDataDir(this), "histories.json"), 0)
        favorites = SavedSet(File(ContextCompat.getDataDir(this), "favorites.json"), 0)
        favoriteTags = SavedSet(File(ContextCompat.getDataDir(this), "favorites_tags.json"), Tag.parse(""))
        searchHistory = SavedSet(File(ContextCompat.getDataDir(this), "search_histories.json"), "")

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