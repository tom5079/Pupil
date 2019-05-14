package xyz.quaver.pupil

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.Reader
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

    private lateinit var snapHelper: PagerSnapHelper

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_reader)

        supportActionBar?.title = intent.getStringExtra("GALLERY_TITLE")

        galleryID = intent.getIntExtra("GALLERY_ID", 0)
        CoroutineScope(Dispatchers.Unconfined).launch {
            reader = async(Dispatchers.IO) {
                val preference = PreferenceManager.getDefaultSharedPreferences(this@ReaderActivity)
                if (preference.getBoolean("use_hiyobi", false)) {
                    try {
                        xyz.quaver.hiyobi.getReader(galleryID)
                    } catch (e: Exception) {
                        getReader(galleryID)
                    }
                }
                getReader(galleryID)
            }
        }

        snapHelper = PagerSnapHelper()

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val attrs = window.attributes

        if (preferences.getBoolean("reader_fullscreen", false)) {
            attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            supportActionBar?.hide()
        } else {
            attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
            supportActionBar?.show()
        }

        window.attributes = attrs

        if (preferences.getBoolean("reader_one_by_one", false)) {
            snapHelper.attachToRecyclerView(reader_recyclerview)
            reader_recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        } else {
            snapHelper.attachToRecyclerView(null)
            reader_recyclerview.layoutManager = LinearLayoutManager(this)
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
                val dialog = AlertDialog.Builder(this).apply {
                    setView(view)
                    with(view.reader_dialog_number_picker) {
                        minValue=1
                        maxValue=gallerySize
                        value=currentPage
                    }
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

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            ItemClickSupport.addTo(this)
                .setOnItemClickListener { _, _, _ ->
                    val attrs = window.attributes
                    val fullscreen = preferences.getBoolean("reader_fullscreen", false)

                    if (fullscreen) {
                        attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                        supportActionBar?.show()
                    } else {
                        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        supportActionBar?.hide()
                    }

                    window.attributes = attrs

                    preferences.edit().putBoolean("reader_fullscreen", !fullscreen).apply()
                }.setOnItemLongClickListener { _, _, _ ->
                    val oneByOne = preferences.getBoolean("reader_one_by_one", false)
                    if (oneByOne) {
                        snapHelper.attachToRecyclerView(null)
                        reader_recyclerview.layoutManager = LinearLayoutManager(context)
                    }
                    else {
                        snapHelper.attachToRecyclerView(reader_recyclerview)
                        reader_recyclerview.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    }

                    (reader_recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage, 0)

                    preferences.edit().putBoolean("reader_one_by_one", !oneByOne).apply()

                    true
                }
        }
    }

    private fun loadImages() {
        fun webpUrlFromUrl(url: URL) = URL(url.toString().replace("/galleries/", "/webp/") + ".webp")

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