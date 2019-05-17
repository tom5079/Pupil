package xyz.quaver.pupil

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.ReaderItem
import xyz.quaver.hitomi.getReader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.util.ItemClickSupport
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ReaderActivity : AppCompatActivity() {

    private val images = ArrayList<String>()
    private var galleryID = 0
    private var gallerySize: Int = 0
    private var currentPage: Int = 0
    private lateinit var reader: Deferred<Reader>
    private var loadJob: Job? = null

    private var isScroll = true
    private var isFullscreen = false

    private val snapHelper = PagerSnapHelper()

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_reader)

        supportActionBar?.title = intent.getStringExtra("GALLERY_TITLE")

        galleryID = intent.getIntExtra("GALLERY_ID", 0)
        reader = CoroutineScope(Dispatchers.IO).async {
            val json = Json(JsonConfiguration.Stable)
            val serializer = ReaderItem.serializer().list
            val preference = PreferenceManager.getDefaultSharedPreferences(this@ReaderActivity)
            val isHiyobi = preference.getBoolean("use_hiyobi", false)

            val cache = when {
                isHiyobi -> File(cacheDir, "imageCache/$galleryID/reader-hiyobi.json")
                else -> File(cacheDir, "imageCache/$galleryID/reader.json")
            }

            if (cache.exists()) {
                val cached = json.parse(serializer, cache.readText())

                if (cached.isNotEmpty())
                    return@async cached
            }

            val reader = when {
                isHiyobi -> {
                        xyz.quaver.hiyobi.getReader(galleryID).let {
                            when {
                                it.isEmpty() -> getReader(galleryID)
                                else -> it
                            }
                        }
                }
                else -> {
                    getReader(galleryID)
                }
            }

            if (reader.isEmpty())
                finish()

            if (!cache.parentFile.exists())
                cache.parentFile.mkdirs()

            cache.writeText(json.stringify(serializer, reader))

            reader
        }

        initView()
        loadImages()
    }

    override fun onResume() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (preferences.getBoolean("security_mode", false))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE)
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.reader, menu)
        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            R.id.reader_menu_page_indicator -> {
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_numberpicker, findViewById(android.R.id.content), false)
                with(view.reader_dialog_number_picker) {
                    minValue=1
                    maxValue=gallerySize
                    value=currentPage
                }
                val dialog = AlertDialog.Builder(this).apply {
                    setView(view)
                }.create()
                view.reader_dialog_ok.setOnClickListener {
                    (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(view.reader_dialog_number_picker.value-1, 0)
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    override fun onBackPressed() {
        if (isScroll and !isFullscreen)
            super.onBackPressed()

        if (isFullscreen) {
            isFullscreen = false
            fullscreen(false)
        }

        if (!isScroll) {
            isScroll = true
            scrollMode(true)
        }
    }

    private fun initView() {
        with(reader_recyclerview) {
            adapter = ReaderAdapter(images)

            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                    if (layoutManager.findFirstVisibleItemPosition() == -1)
                        return
                    currentPage = layoutManager.findFirstVisibleItemPosition()+1
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/$gallerySize"
                }
            })

            ItemClickSupport.addTo(this)
                .setOnItemClickListener { _, _, _ ->
                    if (isScroll) {
                        isScroll = false
                        isFullscreen = true

                        scrollMode(false)
                        fullscreen(true)
                    } else {
                        val smoothScroller = object : LinearSmoothScroller(context) {
                            override fun getVerticalSnapPreference() = SNAP_TO_START
                        }.apply {
                            targetPosition = currentPage
                        }
                        (reader_recyclerview.layoutManager as LinearLayoutManager?)?.startSmoothScroll(smoothScroller)
                    }
                }
        }

        reader_fab_fullscreen.setOnClickListener {
            isFullscreen = true
            fullscreen(isFullscreen)

            reader_fab.close(true)
        }
    }

    private fun fullscreen(isFullscreen: Boolean) {
        with(window.attributes) {
            if (isFullscreen) {
                flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                supportActionBar?.hide()
                this@ReaderActivity.reader_fab.visibility = View.INVISIBLE
            } else {
                flags = flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                supportActionBar?.show()
                this@ReaderActivity.reader_fab.visibility = View.VISIBLE
            }

            window.attributes = this
        }
    }

    private fun scrollMode(isScroll: Boolean) {
        if (isScroll) {
            snapHelper.attachToRecyclerView(null)
            reader_recyclerview.layoutManager = LinearLayoutManager(this)
        } else {
            snapHelper.attachToRecyclerView(reader_recyclerview)
            reader_recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        }

        (reader_recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage-1, 0)
    }

    private fun loadImages() {
        fun webpUrlFromUrl(url: String) = url.replace("/galleries/", "/webp/") + ".webp"

        loadJob = CoroutineScope(Dispatchers.Default).launch {
            val reader = reader.await()

            launch(Dispatchers.Main) {
                with(reader_progressbar) {
                    max = reader.size
                    progress = 0

                    visibility = View.VISIBLE
                }

                gallerySize = reader.size
                menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/$gallerySize"
            }

            reader.chunked(4).forEach { chunked ->
                chunked.map {
                    async(Dispatchers.IO) {
                        val url = if (it.galleryInfo?.haswebp == 1) webpUrlFromUrl(it.url) else it.url

                        val fileName: String

                        with(url) {
                            fileName = substring(lastIndexOf('/')+1)
                        }

                        val cache = File(cacheDir, "/imageCache/$galleryID/$fileName")

                        if (!cache.exists())
                            try {
                                with(URL(url).openConnection() as HttpsURLConnection) {
                                    setRequestProperty("Referer", getReferer(galleryID))

                                    if (!cache.parentFile.exists())
                                        cache.parentFile.mkdirs()

                                    inputStream.copyTo(FileOutputStream(cache))
                                }
                            } catch (e: Exception) {
                                cache.delete()
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