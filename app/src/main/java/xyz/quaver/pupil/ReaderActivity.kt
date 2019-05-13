package xyz.quaver.pupil

import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.pupil.adapters.GalleryAdapter
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ReaderActivity : AppCompatActivity() {

    private val images = ArrayList<String>()
    private var galleryID = 0
    private lateinit var reader: Deferred<Reader>
    private var loadJob: Job? = null
    private var screenMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Pupil", "Reader Opened")
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_reader)

        supportActionBar?.title = intent.getStringExtra("GALLERY_TITLE")

        galleryID = intent.getIntExtra("GALLERY_ID", 0)
        CoroutineScope(Dispatchers.Unconfined).launch {
            reader = async(Dispatchers.IO) {
                Log.d("Pupil", "Loading reader")
                val preference = PreferenceManager.getDefaultSharedPreferences(this@ReaderActivity)
                if (preference.getBoolean("use_hiyobi", false)) {
                    try {
                        xyz.quaver.hiyobi.getReader(galleryID)
                        Log.d("Pupil", "Using Hiyobi.me")
                    } catch (e: Exception) {
                        getReader(galleryID)
                    }
                }
                getReader(galleryID)
            }
        }

        initView()
        Log.d("Pupil", "Reader view init complete")
        loadImages()
    }

    override fun onResume() {
        val preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.getBoolean("security_mode", false))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    private fun initView() {
        reader_recyclerview.adapter = GalleryAdapter(images).apply {
            setOnClick {
                val attrs = window.attributes

                screenMode = (screenMode+1)%2

                when(screenMode) {
                    0 -> {
                        attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                        supportActionBar?.show()
                    }
                    1 -> {
                        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        supportActionBar?.hide()
                    }
                }
                window.attributes = attrs
            }
        }
    }

    private fun loadImages() {
        fun webpUrlFromUrl(url: URL) = URL(url.toString().replace("/galleries/", "/webp/") + ".webp")

        loadJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d("Pupil", "Reader Waiting for the data")
            val reader = reader.await()

            Log.d("Pupil", "Reader Data recieved")

            launch(Dispatchers.Main) {
                with(reader_progressbar) {
                    max = reader.size
                    progress = 0

                    visibility = View.VISIBLE
                }
            }

            reader.chunked(8).forEach { chunked ->
                chunked.map {
                    async(Dispatchers.IO) {
                        val url = if (it.second?.haswebp == 1) webpUrlFromUrl(it.first) else it.first

                        val fileName: String

                        with(url.path) {
                            fileName = substring(lastIndexOf('/')+1)
                        }

                        val cache = File(cacheDir, "/imageCache/$galleryID/$fileName")

                        if (!cache.exists())
                            with(url.openConnection() as HttpsURLConnection) {
                                setRequestProperty("Referer", getReferer(galleryID))

                                if (!cache.parentFile.exists())
                                    cache.parentFile.mkdirs()

                                inputStream.copyTo(FileOutputStream(cache))
                            }

                        cache.absolutePath
                    }
                }.forEach {
                    val cache = it.await()

                    launch(Dispatchers.Main) {
                        images.add(cache)
                        reader_recyclerview.adapter?.notifyItemInserted(images.size - 1)
                        reader_progressbar.progress++
                    }
                }
            }

            launch(Dispatchers.Main) {
                reader_progressbar.visibility = View.GONE
            }
        }
    }
}