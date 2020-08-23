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

package xyz.quaver.pupil.ui

import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.Code
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.util.Histories
import xyz.quaver.pupil.util.download.Cache
import xyz.quaver.pupil.util.download.DownloadWorker
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.timer

class ReaderActivity : AppCompatActivity() {

    private var galleryID = 0
    private var currentPage = 0

    private var isScroll = true
    private var isFullscreen = false
    set(value) {
        field = value

        (reader_recyclerview.adapter as ReaderAdapter).isFullScreen = value

        reader_progressbar.visibility = when {
            value -> View.VISIBLE
            else -> View.GONE
        }
    }

    private val timer = Timer()
    private var autoTimer: Timer? = null

    private val snapHelper = PagerSnapHelper()

    private var menu: Menu? = null

    private lateinit var favorites: Histories

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.reader_loading)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        favorites = (application as Pupil).favorites

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_reader)

        handleIntent(intent)

        FirebaseCrashlytics.getInstance().setCustomKey("GalleryID", galleryID)

        if (galleryID == 0) {
            onBackPressed()
            return
        }

        initView()
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("cache_disable", false)) {
            reader_download_progressbar.visibility = View.GONE
            CoroutineScope(Dispatchers.IO).launch {
                val reader = Cache(this@ReaderActivity).getReader(galleryID)

                launch(Dispatchers.Main) initDownloader@{
                    if (reader == null) {
                        Snackbar
                            .make(reader_layout, R.string.reader_failed_to_find_gallery, Snackbar.LENGTH_INDEFINITE)
                            .show()
                        return@initDownloader
                    }

                    (reader_recyclerview.adapter as ReaderAdapter).apply {
                        this.reader = reader
                        notifyDataSetChanged()
                    }
                    title = reader.galleryInfo.title ?: ""
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${reader.galleryInfo.files.size}"

                    menu?.findItem(R.id.reader_type)?.icon = ContextCompat.getDrawable(this@ReaderActivity,
                        when (reader.code) {
                            Code.HITOMI -> R.drawable.hitomi
                            Code.HIYOBI -> R.drawable.ic_hiyobi
                            else -> android.R.color.transparent
                        })
                }
            }
        } else
            initDownloader()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            val lastPathSegment = uri?.lastPathSegment
            if (uri != null && lastPathSegment != null) {
                galleryID = when (uri.host) {
                    "hitomi.la" ->
                        Regex("([0-9]+).html").find(lastPathSegment)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    "hiyobi.me" -> lastPathSegment.toInt()
                    "e-hentai.org" -> uri.pathSegments[1].toInt()
                    else -> 0
                }
            }
        } else {
            galleryID = intent.getIntExtra("galleryID", 0)
        }
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

        with(menu?.findItem(R.id.reader_menu_favorite)) {
            this ?: return@with

            if (favorites.contains(galleryID))
                (icon as Animatable).start()
        }

        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            R.id.reader_menu_page_indicator -> {
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_numberpicker, reader_layout, false)
                with(view.dialog_number_picker) {
                    minValue=1
                    maxValue=Cache(context).getReaderOrNull(galleryID)?.galleryInfo?.files?.size ?: 0
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
            R.id.reader_menu_favorite -> {
                val id = galleryID
                val favorite = menu?.findItem(R.id.reader_menu_favorite) ?: return true

                if (favorites.contains(id)) {
                    favorites.remove(id)
                    favorite.icon = AnimatedVectorDrawableCompat.create(this, R.drawable.avd_star)
                } else {
                    favorites.add(id)
                    (favorite.icon as Animatable).start()
                }
            }
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()

        timer.cancel()
        (reader_recyclerview?.adapter as? ReaderAdapter)?.timer?.cancel()

        if (!Cache(this).isDownloading(galleryID))
            DownloadWorker.getInstance(this@ReaderActivity).cancel(galleryID)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        //currentPage is 1-based
        return when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(currentPage-2, 0)

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(currentPage, 0)

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun initDownloader() {
        val worker = DownloadWorker.getInstance(this).apply {
            cancel(galleryID)
            queue.add(galleryID)
        }

        timer.schedule(1000, 1000) {
            if (worker.progress.indexOfKey(galleryID) < 0)  //loading
                return@schedule

            if (worker.progress[galleryID] == null) {      //Gallery not found
                timer.cancel()
                Snackbar
                    .make(reader_layout, R.string.reader_failed_to_find_gallery, Snackbar.LENGTH_INDEFINITE)
                    .show()
            }

            runOnUiThread {
                reader_download_progressbar.max = reader_recyclerview.adapter?.itemCount ?: 0
                reader_download_progressbar.progress = worker.progress[galleryID]?.count { it.isInfinite() } ?: 0
                reader_progressbar.max = reader_recyclerview.adapter?.itemCount ?: 0

                if (title == getString(R.string.reader_loading)) {
                    val reader = Cache(this@ReaderActivity).getReaderOrNull(galleryID)

                    if (reader != null) {

                        with (reader_recyclerview.adapter as ReaderAdapter) {
                            this.reader = reader
                            notifyDataSetChanged()
                        }

                        title = reader.galleryInfo.title
                        menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${reader.galleryInfo.files.size}"

                        menu?.findItem(R.id.reader_type)?.icon = ContextCompat.getDrawable(this@ReaderActivity,
                            when (reader.code) {
                                Code.HITOMI -> R.drawable.hitomi
                                Code.HIYOBI -> R.drawable.ic_hiyobi
                                else -> android.R.color.transparent
                            })
                    }
                }

                if (worker.progress[galleryID]?.all { it.isInfinite() } == true) {   //Download finished
                    reader_download_progressbar.visibility = View.GONE

                    animateDownloadFAB(false)
                }
            }
        }
    }

    private fun initView() {
        with(reader_recyclerview) {
            adapter = ReaderAdapter(Glide.with(this@ReaderActivity), galleryID).apply {
                onItemClickListener = {
                    if (isScroll) {
                        isScroll = false
                        isFullscreen = true

                        scrollMode(false)
                        fullscreen(true)
                    } else {
                        (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPosition(currentPage) //Moves to next page because currentPage is 1-based indexing
                    }
                }
            }

            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (dy < 0)
                        this@ReaderActivity.reader_fab.showMenuButton(true)
                    else if (dy > 0)
                        this@ReaderActivity.reader_fab.hideMenuButton(true)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                    if (layoutManager.findFirstVisibleItemPosition() == -1)
                        return
                    currentPage = layoutManager.findFirstVisibleItemPosition()+1
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${recyclerView.adapter!!.itemCount}"
                    this@ReaderActivity.reader_progressbar.progress = currentPage
                }
            })
        }

        with(reader_fab_download) {
            animateDownloadFAB(Cache(context).isDownloading(galleryID)) //If download in progress, animate button

            setOnClickListener {
                if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("cache_disable", false))
                    Toast.makeText(context, R.string.settings_download_when_cache_disable_warning, Toast.LENGTH_SHORT).show()
                else {
                    if (Cache(context).isDownloading(galleryID)) {
                        Cache(context).setDownloading(galleryID, false)

                        animateDownloadFAB(false)
                    } else {
                        Cache(context).setDownloading(galleryID, true)
                        animateDownloadFAB(true)
                    }
                }
            }
        }

        with(reader_fab_retry) {
            setImageResource(R.drawable.refresh)
            setOnClickListener {
                DownloadWorker.getInstance(context).let {
                    it.cancel(galleryID)
                    it.queue.add(galleryID)
                }
            }
        }

        with(reader_fab_auto) {
            setImageResource(R.drawable.clock_start)
            setOnClickListener {
                if (autoTimer == null) {
                    autoTimer = timer(initialDelay = 10000L, period = 10000L) {
                        CoroutineScope(Dispatchers.Main).launch {
                            with(this@ReaderActivity.reader_recyclerview) {
                                val lastItem =
                                    (layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()

                                if (lastItem < adapter!!.itemCount - 1)
                                    (layoutManager as LinearLayoutManager).scrollToPosition(lastItem + 1)
                            }
                        }
                    }
                    setImageResource(R.drawable.clock_end)
                } else {
                    autoTimer?.cancel()
                    autoTimer = null
                    setImageResource(R.drawable.clock_start)
                }
            }
        }

        with(reader_fab_fullscreen) {
            setImageResource(R.drawable.ic_fullscreen)
            setOnClickListener {
                isFullscreen = true
                fullscreen(isFullscreen)

                this@ReaderActivity.reader_fab.close(true)
            }
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

        reader_recyclerview.adapter = reader_recyclerview.adapter   // Force to redraw
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

    private fun animateDownloadFAB(animate: Boolean) {
        with(reader_fab_download) {
            if (animate) {
                val icon = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_downloading)

                icon?.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        val worker = DownloadWorker.getInstance(context)
                        if (worker.progress[galleryID]?.all { it.isInfinite() } == true) // If download is finished, stop animating
                            post {
                                setImageResource(R.drawable.ic_download)
                                labelText = getString(R.string.reader_fab_download_cancel)
                            }
                        else                                                            // Or continue animate
                            post {
                                icon.start()
                                labelText = getString(R.string.reader_fab_download_cancel)
                            }
                    }
                })

                setImageDrawable(icon)
                icon?.start()
            } else {
                setImageResource(R.drawable.ic_download)
                labelText = getString(R.string.reader_fab_download)
            }
        }
    }
}