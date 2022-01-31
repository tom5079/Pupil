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

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.webkit.WebViewCompat
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.fresco.FrescoImageLoader
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import xyz.quaver.io.FileX
import xyz.quaver.pupil.hitomi.evaluationContext
import xyz.quaver.pupil.types.JavascriptException
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

@SuppressLint("StaticFieldLeak")
lateinit var webView: WebView
val _webViewFlow = MutableSharedFlow<Pair<String, String?>>()
val webViewFlow = _webViewFlow.asSharedFlow()
var webViewReady = false
var oldWebView = false
private var reloadJob: Job? = null

fun reloadWebView() {
    if (reloadJob?.isActive == true) return

    reloadJob = CoroutineScope(Dispatchers.IO).launch {
        webViewReady = false
        oldWebView = false

        evaluationContext.cancelChildren(CancellationException("reload"))

        runCatching {
            URL(
                if (BuildConfig.DEBUG)
                    "https://tom5079.github.io/PupilSources/hitomi-dev.html"
                else
                    "https://tom5079.github.io/PupilSources/hitomi.html"
            ).readText()
        }.getOrNull()?.let { html ->
            launch(Dispatchers.Main) {
                webView.loadDataWithBaseURL(
                    "https://hitomi.la/",
                    html,
                    "text/html",
                    null,
                    null
                )
            }
        }
    }
}

private var htmlVersion: String = ""
fun reloadWhenFailedOrUpdate() = CoroutineScope(Dispatchers.Default).launch {
    while (true) {
        if (
            (!webViewReady && !oldWebView) ||
            runCatching {
                URL(
                    if (BuildConfig.DEBUG)
                        "https://tom5079.github.io/PupilSources/hitomi-dev.html.ver"
                    else
                        "https://tom5079.github.io/PupilSources/hitomi.html.ver"
                ).readText()
            }.getOrNull().let { version ->
                (!version.isNullOrEmpty() && version != htmlVersion).also {
                    if (it) htmlVersion = version!!
                }
            }
        ) {
            reloadWebView()
        }

        delay(if (webViewReady) 10000 else 1000)
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun initWebView(context: Context) {
    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

    webView = WebView(context).apply {
        with (settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        userAgent = settings.userAgentString

        webViewClient = object: WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("try { self_test() } catch (err) { 'err' }") {
                    val result: String = Json.decodeFromString(it)

                    oldWebView = result == "es2020_unsupported";
                    webViewReady = result == "OK";
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    FirebaseCrashlytics.getInstance().log(
                        "onReceivedError: ${error?.description}"
                    )
                }
            }
        }

        webChromeClient = object: WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                FirebaseCrashlytics.getInstance().log(
                    "onConsoleMessage: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})"
                )

                return super.onConsoleMessage(consoleMessage)
            }
        }

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onResult(uid: String, result: String) {
                CoroutineScope(Dispatchers.Unconfined).launch {
                    _webViewFlow.emit(uid to result)
                }
            }
            @JavascriptInterface
            fun onError(uid: String, script: String, message: String, stack: String) {
                CoroutineScope(Dispatchers.Unconfined).launch {
                    _webViewFlow.emit(uid to "")
                }

                FirebaseCrashlytics.getInstance().recordException(
                    JavascriptException("onError script: $script\nmessage: $message\nstack: $stack")
                )
            }
        }, "Callback")
    }

    reloadWhenFailedOrUpdate()
}

lateinit var userAgent: String

class Pupil : Application() {

    companion object {
        lateinit var instance: Pupil
            private set
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate() {
        instance = this

        initWebView(this)

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
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
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