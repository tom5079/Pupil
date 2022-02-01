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
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import app.cash.zipline.QuickJs
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.fresco.FrescoImageLoader
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import xyz.quaver.io.FileX
import xyz.quaver.pupil.hitomi.evaluationContext
import xyz.quaver.pupil.hitomi.readText
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.*
import java.io.File
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
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
    }

private var version = ""
var runtimeReady = false
    private set
lateinit var runtime: QuickJs
    private set

class Pupil : Application() {

    companion object {
        lateinit var instance: Pupil
            private set
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(evaluationContext) {
                runtime = QuickJs.create()
            }
            while (true) {
                kotlin.runCatching {
                    val newVersion = URL("https://tom5079.github.io/PupilSources/hitomi.html.ver").readText()

                    if (version != newVersion) {
                        runtimeReady = false
                        version = newVersion
                        evaluationContext.cancelChildren()
                        withContext(evaluationContext) {
                            Log.d("PUPILD", "UPDATE!")
                            runtime.evaluate(URL("https://tom5079.github.io/PupilSources/assets/js/gg.js").readText())
                            runtimeReady = true
                        }
                    }
                }

                delay(10000)
            }
        }
    }

    override fun onCreate() {
        instance = this

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val userID = Preferences["user_id", ""].let {  userID ->
            if (userID.isEmpty()) UUID.randomUUID().toString().also { Preferences["user_id"] = it }
            else userID
        }

        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().setUserId(userID)

        val proxyInfo = getProxyInfo()

        clientBuilder = OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .proxyInfo(proxyInfo)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.81 Safari/537.36")
                    .header("Referer", "https://hitomi.la/")
                    .build()

                val tag = request.tag() ?: return@addInterceptor chain.proceed(request)

                interceptors[tag::class]?.invoke(chain) ?: chain.proceed(request)
            }.apply {
                (Preferences.get<String>("max_concurrent_download").toIntOrNull() ?: 0).let {
                    if (it != 0)
                        dispatcher(Dispatcher(Executors.newFixedThreadPool(it)))
                }
            }

        try {
            Preferences.get<String>("download_folder").also {
                if (it.startsWith("content://"))
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

        histories = SavedSet(File(ContextCompat.getDataDir(this), "histories.json"), 0)
        favorites = SavedSet(File(ContextCompat.getDataDir(this), "favorites.json"), 0)
        favoriteTags = SavedSet(File(ContextCompat.getDataDir(this), "favorites_tags.json"), Tag.parse(""))
        searchHistory = SavedSet(File(ContextCompat.getDataDir(this), "search_histories.json"), "")

        favoriteTags.filter { it.tag.contains('_') }.forEach {
            favoriteTags.remove(it)
        }

        /*
        if (BuildConfig.DEBUG)
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)*/

        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        BigImageViewer.initialize(
            FrescoImageLoader.with(
                this,
                OkHttpImagePipelineConfigFactory
                    .newBuilder(this, client)
                    .build()
            )
        )

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