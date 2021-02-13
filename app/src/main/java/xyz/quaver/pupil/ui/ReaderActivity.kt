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
import android.os.Bundle
import android.view.*
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.orhanobut.logger.Logger
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.di.instance
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.databinding.ReaderActivityBinding
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.SavedSourceSet
import xyz.quaver.pupil.util.source

class ReaderActivity : BaseActivity(), DIAware {

    override val di by di()

    private var source = ""
    private var itemID = ""

    private var currentPage = 0

    private var isScroll = true
    private var isFullscreen = false
    set(value) {
        field = value
    }

    private val snapHelper = PagerSnapHelper()
    private var menu: Menu? = null

    private lateinit var binding: ReaderActivityBinding
    private val model: ReaderViewModel by viewModels()

    private val favorites: SavedSourceSet by instance(tag = "favorites")
    private val histories: SavedSourceSet by instance(tag = "histories")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.reader_loading)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        handleIntent(intent)
        if (itemID.isEmpty()) {
            onBackPressed()
            return
        }

        histories.add(source, itemID)
        FirebaseCrashlytics.getInstance().setCustomKey("GalleryID", itemID)

        Logger.d(histories)

        model.readerItems.observe(this) {
            (binding.recyclerview.adapter as ReaderAdapter).submitList(it.toMutableList())

            binding.downloadProgressbar.apply {
                max = it.size
                progress = it.count { it.image != null }

                visibility =
                    if (progress == max)
                        View.GONE
                    else
                        View.VISIBLE
            }
        }

        model.title.observe(this) {
            title = it
        }

        model.load(source, itemID)

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
                source = uri.host ?: ""
                itemID = when (uri.host) {
                    "hitomi.la" ->
                        Regex("([0-9]+).html").find(lastPathSegment)?.groupValues?.get(1) ?: ""
                    "hiyobi.me" -> lastPathSegment
                    "e-hentai.org" -> uri.pathSegments[1]
                    else -> ""
                }
            }
        } else {
            source = intent.getStringExtra("source") ?: ""
            itemID = intent.getStringExtra("id") ?: ""
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.reader, menu)

        menu?.forEach {
            when (it.itemId) {
                R.id.reader_menu_favorite -> {
                    if (favorites.map[source]?.contains(itemID) == true)
                        (it.icon as Animatable).start()
                }
                R.id.source -> {
                    it.setIcon(source(source).value.iconResID)
                }
            }
        }

        this.menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.reader_menu_favorite -> {
                val id = itemID
                val favorite = menu?.findItem(R.id.reader_menu_favorite) ?: return true

                if (favorites.map[source]?.contains(id) == true) {
                    favorites.remove(source, id)
                    favorite.icon = AnimatedVectorDrawableCompat.create(this, R.drawable.avd_star)
                } else {
                    favorites.add(source, id)
                    (favorite.icon as Animatable).start()
                }
            }
        }

        return true
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
        return when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                (binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage-1, 0)

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                (binding.recyclerview.layoutManager as LinearLayoutManager?)?.scrollToPositionWithOffset(currentPage+1, 0)

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun initView() {
        with (binding.recyclerview) {
            adapter = ReaderAdapter().apply {
                onItemClickListener = {
                    if (isScroll) {
                        isScroll = false
                        isFullscreen = true

                        scrollMode(false)
                        fullscreen(true)
                    } else {
                        binding.recyclerview.layoutManager?.scrollToPosition(currentPage+1) // Moves to next page because currentPage is 1-based indexing
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

                    currentPage = layoutManager.findFirstVisibleItemPosition()
                    menu?.findItem(R.id.reader_menu_page_indicator)?.title = "${currentPage+1}/${recyclerView.adapter!!.itemCount}"
                }
            })

            itemAnimator = null
        }

        with (binding.retryFab) {
            setImageResource(R.drawable.refresh)
            setOnClickListener {

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
        (binding.recyclerview.adapter as ReaderAdapter).fullscreen = isFullscreen

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
                    extraLayoutSpace[0] = 10
                    extraLayoutSpace[1] = 10
                }
            }
        }

        (binding.recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage, 0)
    }
}