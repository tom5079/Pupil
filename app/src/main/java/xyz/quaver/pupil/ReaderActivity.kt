package xyz.quaver.pupil

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.util.GalleryDownloader
import xyz.quaver.pupil.util.ItemClickSupport

class ReaderActivity : AppCompatActivity() {

    private val images = ArrayList<String>()
    private lateinit var galleryBlock: GalleryBlock
    private var gallerySize = 0
    private var currentPage = 0

    private var isScroll = true
    private var isFullscreen = false

    private lateinit var downloader: GalleryDownloader

    private val snapHelper = PagerSnapHelper()

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_reader)

        galleryBlock = Json(JsonConfiguration.Stable).parse(
            GalleryBlock.serializer(),
            intent.getStringExtra("galleryblock")
        )

        supportActionBar?.title = galleryBlock.title
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        initDownloader()

        initView()

        if (!downloader.download)
            downloader.start()
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
                with(view.dialog_number_picker) {
                    minValue=1
                    maxValue=gallerySize
                    value=currentPage
                }
                val dialog = AlertDialog.Builder(this).apply {
                    setView(view)
                }.create()
                view.dialog_ok.setOnClickListener {
                    (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(view.dialog_number_picker.value-1, 0)
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!downloader.download)
            downloader.cancel()
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

    private fun initDownloader() {
        var d: GalleryDownloader? = GalleryDownloader.get(galleryBlock.id)

        if (d == null) {
            try {
                d = GalleryDownloader(this, galleryBlock)
            } catch (e: IOException) {
                Snackbar.make(reader_layout, R.string.unable_to_connect, Snackbar.LENGTH_LONG).show()
                finish()
                return
            }
        }

        downloader = d.apply {
            onReaderLoadedHandler = {
                CoroutineScope(Dispatchers.Main).launch {
                    with(reader_progressbar) {
                        max = it.size
                        progress = 0

                        visibility = View.VISIBLE
                    }

                    gallerySize = it.size
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${it.size}"
                }
            }
            onProgressHandler = {
                CoroutineScope(Dispatchers.Main).launch {
                    reader_progressbar.progress = it
                }
            }
            onDownloadedHandler = {
                val item = it.toList()
                CoroutineScope(Dispatchers.Main).launch {
                    if (images.isEmpty()) {
                        images.addAll(item)
                        reader_recyclerview.adapter?.notifyDataSetChanged()
                    } else {
                        images.add(item.last())
                        reader_recyclerview.adapter?.notifyItemInserted(images.size-1)
                    }
                }
            }
            onErrorHandler = {
                if (it is IOException)
                    Snackbar.make(reader_layout, R.string.unable_to_connect, Snackbar.LENGTH_LONG).show()
                downloader.download = false
            }
            onCompleteHandler = {
                CoroutineScope(Dispatchers.Main).launch {
                    reader_progressbar.visibility = View.GONE
                }
            }
            onNotifyChangedHandler = { notify ->
                val fab = reader_fab_download

                runOnUiThread {
                    if (notify) {
                        val icon = AnimatedVectorDrawableCompat.create(this, R.drawable.ic_downloading)
                        icon?.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable?) {
                                if (downloader.download)
                                    fab.post {
                                        icon.start()
                                        fab.labelText = getString(R.string.reader_fab_download_cancel)
                                    }
                                else
                                    fab.post {
                                        fab.setImageResource(R.drawable.ic_download)
                                        fab.labelText = getString(R.string.reader_fab_download)
                                    }
                            }
                        })

                        fab.setImageDrawable(icon)
                        icon?.start()
                    } else {
                        runOnUiThread {
                            fab.setImageResource(R.drawable.ic_download)
                        }
                    }
                }
            }
        }

        if (downloader.download) {
            downloader.invokeOnReaderLoaded()
            downloader.invokeOnNotifyChanged()
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

        reader_fab_download.setOnClickListener {
            downloader.download = !downloader.download

            if (!downloader.download)
                downloader.clearNotification()
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
}