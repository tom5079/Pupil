package xyz.quaver.pupil

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_gallery.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.pupil.adapters.GalleryAdapter
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GalleryActivity : AppCompatActivity() {

    private val images = ArrayList<String>()
    private var galleryID = 0
    private lateinit var reader: Deferred<Reader>
    private var loadJob: Job? = null
    private var screenMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_gallery)

        supportActionBar?.title = intent.getStringExtra("GALLERY_TITLE")

        galleryID = intent.getIntExtra("GALLERY_ID", 0)
        CoroutineScope(Dispatchers.Unconfined).launch {
            reader = async(Dispatchers.IO) {
                val preference = PreferenceManager.getDefaultSharedPreferences(this@GalleryActivity)
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
        loadImages()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    private fun initView() {
        gallery_recyclerview.adapter = GalleryAdapter(images).apply {
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
            val reader = reader.await()

            launch(Dispatchers.Main) {
                with(gallery_progressbar) {
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
                        gallery_recyclerview.adapter?.notifyItemInserted(images.size - 1)
                        gallery_progressbar.progress++
                    }
                }
            }

            launch(Dispatchers.Main) {
                gallery_progressbar.visibility = View.GONE
            }
        }
    }
}