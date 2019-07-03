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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.crashlytics.android.Crashlytics
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.ImplicitReflectionSerializer
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.util.GalleryDownloader
import xyz.quaver.pupil.util.Histories
import xyz.quaver.pupil.util.ItemClickSupport

class ReaderActivity : AppCompatActivity() {

    private var galleryID = 0
    private val images = ArrayList<String>()
    private var gallerySize = 0
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

    private lateinit var downloader: GalleryDownloader

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

        Crashlytics.setInt("GalleryID", galleryID)

        if (galleryID == 0) {
            onBackPressed()
            return
        }

        initDownloader()

        initView()

        if (!downloader.download)
            downloader.start()
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
                val nonNumber = Regex("[^-?0-9]+")

                galleryID = when (uri.host) {
                    "hitomi.la" -> lastPathSegment.replace(nonNumber, "").toInt()
                    "히요비.asia" -> lastPathSegment.toInt()
                    "xn--9w3b15m8vo.asia" -> lastPathSegment.toInt()
                    "e-hentai.org" -> uri.pathSegments[1].toInt()
                    else -> return
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

    @UseExperimental(ImplicitReflectionSerializer::class)
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

        if (::downloader.isInitialized && !downloader.download)
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
        var d: GalleryDownloader? = GalleryDownloader.get(galleryID)

        if (d == null) {
            try {
                d = GalleryDownloader(this, galleryID)
            } catch (e: IOException) {
                Snackbar.make(reader_layout, R.string.unable_to_connect, Snackbar.LENGTH_LONG).show()
                finish()
                return
            }
        }

        downloader = d.apply {
            onReaderLoadedHandler = {
                CoroutineScope(Dispatchers.Main).launch {
                    title = it.title
                    with(reader_download_progressbar) {
                        max = it.readerItems.size
                        progress = 0
                    }
                    with(reader_progressbar) {
                        max = it.readerItems.size
                        progress = 0
                    }

                    gallerySize = it.readerItems.size
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${it.readerItems.size}"
                }
            }
            onProgressHandler = {
                CoroutineScope(Dispatchers.Main).launch {
                    reader_download_progressbar.progress = it
                    menu?.findItem(R.id.reader_menu_use_hiyobi)?.isVisible = downloader.useHiyobi
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
                    reader_download_progressbar.visibility = View.GONE
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
                    this@ReaderActivity.reader_progressbar.progress = currentPage
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
                        (reader_recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPosition(currentPage)
                    }
                }
        }

        with(reader_fab_download) {
            setImageResource(R.drawable.ic_download)
            setOnClickListener {
                downloader.download = !downloader.download

                if (!downloader.download)
                    downloader.clearNotification()
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