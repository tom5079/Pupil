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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.databinding.NumberpickerDialogBinding
import xyz.quaver.pupil.databinding.ReaderActivityBinding
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager

class ReaderActivity : BaseActivity() {

    private var galleryID = ""
    private var currentPage = 0

    private var isScroll = true
    private var isFullscreen = false
    set(value) {
        field = value

        (binding.recyclerview.adapter as ReaderAdapter).isFullScreen = value
    }

    private lateinit var cache: Cache
    var downloader: DownloadService? = null
    private val conn = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloader = (service as DownloadService.Binder).service.also {
                it.priority = ""

                if (!it.progress.containsKey(galleryID))
                    DownloadService.download(this@ReaderActivity, galleryID, true)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloader = null
        }
    }

    private val snapHelper = PagerSnapHelper()
    private var menu: Menu? = null

    private lateinit var binding: ReaderActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.reader_loading)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        handleIntent(intent)
        cache = Cache.getInstance(this, galleryID)
        FirebaseCrashlytics.getInstance().setCustomKey("GalleryID", galleryID)

        if (galleryID.isEmpty()) {
            onBackPressed()
            return
        }

        initDownloadListener()
        initView()
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
                        Regex("([0-9]+).html").find(lastPathSegment)?.groupValues?.get(1) ?: ""
                    "hiyobi.me" -> lastPathSegment
                    "e-hentai.org" -> uri.pathSegments[1]
                    else -> ""
                }
            }
        } else {
            galleryID = intent.getStringExtra("galleryID") ?: ""
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.reader, menu)

        with (menu?.findItem(R.id.reader_menu_favorite)) {
            this ?: return@with

            if (favorites.contains(galleryID))
                (icon as Animatable).start()
        }

        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.reader_menu_page_indicator -> {
                // TODO: Switch to DialogFragment
                val binding = NumberpickerDialogBinding.inflate(layoutInflater, binding.root, false)

                with (binding.numberPicker) {
                    minValue = 1
                    maxValue = cache.metadata.reader?.files?.size ?: 0
                    value = currentPage
                }
                val dialog = AlertDialog.Builder(this).apply {
                    setView(binding.root)
                }.create()
                binding.okButton.setOnClickListener {
                    (this@ReaderActivity.binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(binding.numberPicker.value-1, 0)
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

    override fun onResume() {
        super.onResume()

        bindService(Intent(this, DownloadService::class.java), conn, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()

        if (downloader != null)
            unbindService(conn)

        downloader?.priority = galleryID
    }

    override fun onDestroy() {
        super.onDestroy()

        update = false

        if (!DownloadManager.getInstance(this).isDownloading(galleryID))
            DownloadService.cancel(this, galleryID)
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
                (binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage-2, 0)

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                (binding.recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(currentPage, 0)

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private var update = true
    private fun initDownloadListener() {
        CoroutineScope(Dispatchers.Main).launch {
            while (update) {
                delay(1000)

                val downloader = downloader ?: continue

                if (!downloader.progress.containsKey(galleryID))  //loading
                    continue

                if (downloader.progress[galleryID]?.isEmpty() == true) {      //Gallery not found
                    update = false
                    Snackbar
                        .make(binding.root, R.string.reader_failed_to_find_gallery, Snackbar.LENGTH_INDEFINITE)
                        .show()

                    return@launch
                }

                binding.downloadProgressbar.max = binding.recyclerview.adapter?.itemCount ?: 0
                binding.downloadProgressbar.progress =
                    downloader.progress[galleryID]?.count { it.isInfinite() } ?: 0

                if (title == getString(R.string.reader_loading)) {
                    val reader = cache.metadata.reader

                    if (reader != null) {
                        with (binding.recyclerview.adapter as ReaderAdapter) {
                            this.reader = reader
                            notifyDataSetChanged()
                        }

                        title = reader.title
                        menu?.findItem(R.id.reader_menu_page_indicator)?.title =
                            "$currentPage/${reader.files.size}"

                        menu?.findItem(R.id.reader_type)?.icon = ContextCompat.getDrawable(
                            this@ReaderActivity,
                            R.drawable.hitomi
                            /*
                            when (reader.code) {
                                Code.HITOMI -> R.drawable.hitomi
                                Code.HIYOBI -> R.drawable.ic_hiyobi
                                else -> android.R.color.transparent
                            }*/
                        )
                    }
                }

                if (downloader.isCompleted(galleryID)) {   //Download finished
                    binding.downloadProgressbar.visibility = View.GONE

                    animateDownloadFAB(false)
                }
            }
        }
    }

    private fun initView() {
        with (binding.recyclerview) {
            adapter = ReaderAdapter(this@ReaderActivity, galleryID).apply {
                onItemClickListener = {
                    if (isScroll) {
                        isScroll = false
                        isFullscreen = true

                        scrollMode(false)
                        fullscreen(true)
                    } else {
                        (binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage, 0) //Moves to next page because currentPage is 1-based indexing
                    }
                }
            }

            addOnScrollListener(object: RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (dy < 0)
                        binding.fab.showMenuButton(true)
                    else if (dy > 0)
                        binding.fab.hideMenuButton(true)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                    if (layoutManager.findFirstVisibleItemPosition() == -1)
                        return
                    currentPage = layoutManager.findFirstVisibleItemPosition()+1
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "$currentPage/${recyclerView.adapter!!.itemCount}"
                }
            })
        }

        with (binding.downloadFab) {
            animateDownloadFAB(DownloadManager.getInstance(this@ReaderActivity).getDownloadFolder(galleryID) != null) //If download in progress, animate button

            setOnClickListener {
                val downloadManager = DownloadManager.getInstance(this@ReaderActivity)

                if (downloadManager.isDownloading(galleryID)) {
                    downloadManager.deleteDownloadFolder(galleryID)
                    animateDownloadFAB(false)
                } else {
                    downloadManager.addDownloadFolder(galleryID)
                    DownloadService.download(context, galleryID, true)
                    animateDownloadFAB(true)
                }
            }
        }

        with (binding.retryFab) {
            setImageResource(R.drawable.refresh)
            setOnClickListener {
                DownloadService.download(context, galleryID)
            }
        }

        with (binding.fullscreenFab) {
            setImageResource(R.drawable.ic_fullscreen)
            setOnClickListener {
                isFullscreen = true
                fullscreen(isFullscreen)

                binding.fab.close(true)
            }
        }
    }

    private fun fullscreen(isFullscreen: Boolean) {
        with (window.attributes) {
            if (isFullscreen) {
                flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                supportActionBar?.hide()
                binding.fab.visibility = View.INVISIBLE
                binding.scroller.let {
                    it.handleWidth = resources.getDimensionPixelSize(R.dimen.thumb_height)
                    it.handleHeight = resources.getDimensionPixelSize(R.dimen.thumb_width)
                    it.handleDrawable = ContextCompat.getDrawable(this@ReaderActivity, R.drawable.thumb_horizontal)
                    it.fastScrollDirection = RecyclerViewFastScroller.FastScrollDirection.HORIZONTAL
                }
            } else {
                flags = flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                supportActionBar?.show()
                binding.fab.visibility = View.VISIBLE
                binding.scroller.let {
                    it.handleWidth = resources.getDimensionPixelSize(R.dimen.thumb_width)
                    it.handleHeight = resources.getDimensionPixelSize(R.dimen.thumb_height)
                    it.handleDrawable = ContextCompat.getDrawable(this@ReaderActivity, R.drawable.thumb)
                    it.fastScrollDirection = RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                }
            }

            window.attributes = this
        }

        binding.recyclerview.adapter = binding.recyclerview.adapter   // Force to redraw
    }

    private fun scrollMode(isScroll: Boolean) {
        if (isScroll) {
            snapHelper.attachToRecyclerView(null)
            binding.recyclerview.layoutManager = LinearLayoutManager(this)
        } else {
            snapHelper.attachToRecyclerView(binding.recyclerview)
            binding.recyclerview.layoutManager = object: LinearLayoutManager(this, HORIZONTAL, Preferences["rtl", false]) {
                override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
                    extraLayoutSpace.fill(600)
                }
            }
        }

        (binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage-1, 0)
    }

    private fun animateDownloadFAB(animate: Boolean) {
        with (binding.downloadFab) {
            if (animate) {
                val icon = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_downloading)

                icon?.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        if (downloader?.isCompleted(galleryID) == true) // If download is finished, stop animating
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