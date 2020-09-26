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

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.mlkit.vision.face.Face
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.activity_reader.view.*
import kotlinx.android.synthetic.main.dialog_numberpicker.view.*
import kotlinx.android.synthetic.main.reader_eye_card.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.quaver.Code
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.ReaderAdapter
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.camera
import xyz.quaver.pupil.util.closeCamera
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.startCamera
import java.util.*
import kotlin.concurrent.schedule

class ReaderActivity : BaseActivity() {

    private var galleryID = 0
    private var currentPage = 0

    private var isScroll = true
    private var isFullscreen = false
    set(value) {
        field = value

        (reader_recyclerview.adapter as ReaderAdapter).isFullScreen = value
    }

    private lateinit var cache: Cache
    var downloader: DownloadService? = null
    private val conn = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloader = (service as DownloadService.Binder).service.also {
                if (!it.progress.containsKey(galleryID))
                    DownloadService.download(this@ReaderActivity, galleryID, true)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloader = null
        }
    }

    private val timer = Timer()
    private val snapHelper = PagerSnapHelper()
    private var menu: Menu? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted)
            toggleCamera()
        else
            AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.camera_denied)
                .setPositiveButton(android.R.string.ok) { _, _ ->}
                .show()
    }

    enum class Eye {
        LEFT,
        RIGHT
    }

    private var cameraEnabled = false
    private var eyeType: Eye? = null
    private var eyeTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        title = getString(R.string.reader_loading)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        handleIntent(intent)
        cache = Cache.getInstance(this, galleryID)
        FirebaseCrashlytics.getInstance().setCustomKey("GalleryID", galleryID)

        if (galleryID == 0) {
            onBackPressed()
            return
        }

        if (Preferences["cache_disable"]) {
            reader_download_progressbar.visibility = View.GONE
            CoroutineScope(Dispatchers.IO).launch {
                val reader = cache.getReader()

                launch(Dispatchers.Main) initDownloader@{
                    if (reader == null) {
                        Snackbar
                            .make(reader_layout, R.string.reader_failed_to_find_gallery, Snackbar.LENGTH_INDEFINITE)
                            .show()
                        return@initDownloader
                    }

                    histories.add(galleryID)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.reader_menu_page_indicator -> {
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_numberpicker, reader_layout, false)
                with(view.dialog_number_picker) {
                    minValue = 1
                    maxValue = cache.metadata.reader?.galleryInfo?.files?.size ?: 0
                    value = currentPage
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

    override fun onResume() {
        super.onResume()

        bindService(Intent(this, DownloadService::class.java), conn, BIND_AUTO_CREATE)

        if (cameraEnabled)
            startCamera(this, cameraCallback)
    }

    override fun onPause() {
        super.onPause()
        closeCamera()

        if (downloader != null)
            unbindService(conn)

        if (!DownloadManager.getInstance(this).isDownloading(galleryID))
            DownloadService.cancel(this, galleryID)
    }

    override fun onDestroy() {
        super.onDestroy()

        timer.cancel()
        (reader_recyclerview?.adapter as? ReaderAdapter)?.timer?.cancel()
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

    private fun initDownloadListener() {
        timer.schedule(1000, 1000) {
            val downloader = downloader ?: return@schedule

            if (!downloader.progress.containsKey(galleryID))  //loading
                return@schedule

            if (downloader.progress[galleryID]?.isEmpty() == true) {      //Gallery not found
                timer.cancel()
                Snackbar
                    .make(reader_layout, R.string.reader_failed_to_find_gallery, Snackbar.LENGTH_INDEFINITE)
                    .show()
            }

            histories.add(galleryID)

            runOnUiThread {
                reader_download_progressbar.max = reader_recyclerview.adapter?.itemCount ?: 0
                reader_download_progressbar.progress = downloader.progress[galleryID]?.count { it.isInfinite() } ?: 0

                if (title == getString(R.string.reader_loading)) {
                    val reader = cache.metadata.reader

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

                if (downloader.isCompleted(galleryID)) {   //Download finished
                    reader_download_progressbar.visibility = View.GONE

                    animateDownloadFAB(false)
                }
            }
        }
    }

    private fun initView() {
        with(reader_recyclerview) {
            adapter = ReaderAdapter(this@ReaderActivity, galleryID).apply {
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

                }
            })
        }

        with(reader_fab_download) {
            animateDownloadFAB(DownloadManager.getInstance(this@ReaderActivity).getDownloadFolder(galleryID) != null) //If download in progress, animate button

            setOnClickListener {
                if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("cache_disable", false))
                    Toast.makeText(context, R.string.settings_download_when_cache_disable_warning, Toast.LENGTH_SHORT).show()
                else {
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
        }

        with(reader_fab_retry) {
            setImageResource(R.drawable.refresh)
            setOnClickListener {
                DownloadService.download(context, galleryID)
            }
        }

        with(reader_fab_auto) {
            setImageResource(R.drawable.eye_white)
            setOnClickListener {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                        toggleCamera()
                    }
                    Build.VERSION.SDK_INT >= 23 && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                        AlertDialog.Builder(this@ReaderActivity)
                            .setTitle(R.string.warning)
                            .setMessage(R.string.camera_denied)
                            .setPositiveButton(android.R.string.ok) { _, _ ->}
                            .show()
                    }
                    else ->
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                this@ReaderActivity.scroller.let {
                    it.handleWidth = resources.getDimensionPixelSize(R.dimen.thumb_height)
                    it.handleHeight = resources.getDimensionPixelSize(R.dimen.thumb_width)
                    it.handleDrawable = ContextCompat.getDrawable(this@ReaderActivity, R.drawable.thumb_horizontal)
                    it.fastScrollDirection = RecyclerViewFastScroller.FastScrollDirection.HORIZONTAL
                }
            } else {
                flags = flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                supportActionBar?.show()
                this@ReaderActivity.reader_fab.visibility = View.VISIBLE
                this@ReaderActivity.scroller.let {
                    it.handleWidth = resources.getDimensionPixelSize(R.dimen.thumb_width)
                    it.handleHeight = resources.getDimensionPixelSize(R.dimen.thumb_height)
                    it.handleDrawable = ContextCompat.getDrawable(this@ReaderActivity, R.drawable.thumb)
                    it.fastScrollDirection = RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                }
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
            reader_recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, Preferences["rtl", false])
        }

        (reader_recyclerview.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPage-1, 0)
    }

    private fun animateDownloadFAB(animate: Boolean) {
        with(reader_fab_download) {
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

    val cameraCallback: (List<Face>) -> Unit = callback@{ faces ->
        eye_card.dot.let {
            it.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.Main).launch {
                delay(50)
                it.visibility = View.INVISIBLE
            }
        }

        if (faces.size != 1)
            ContextCompat.getDrawable(this, R.drawable.eye_off).let {
                with(eye_card) {
                    left_eye.setImageDrawable(it)
                    right_eye.setImageDrawable(it)
                }

                return@callback
            }

        val (left, right) = Pair(
            faces[0].rightEyeOpenProbability?.let { it > 0.4 } == true,
            faces[0].leftEyeOpenProbability?.let { it > 0.4 } == true
        )

        with(eye_card) {
            left_eye.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    if (left) R.drawable.eye else R.drawable.eye_closed
                )
            )
            right_eye.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    if (right) R.drawable.eye else R.drawable.eye_closed
                )
            )
        }

        when {
            // Both closed / opened
            !left.xor(right) -> {
                eyeType = null
                eyeTime = 0L
            }
            !left -> {
                if (eyeType != Eye.LEFT) {
                    eyeType = Eye.LEFT
                    eyeTime = System.currentTimeMillis()
                }
            }
            !right -> {
                if (eyeType != Eye.RIGHT) {
                    eyeType = Eye.RIGHT
                    eyeTime = System.currentTimeMillis()
                }
            }
        }

        if (eyeType != null && System.currentTimeMillis() - eyeTime > 100) {
            (this@ReaderActivity.reader_recyclerview.layoutManager as LinearLayoutManager).let {
                it.scrollToPositionWithOffset(when(eyeType!!) {
                    Eye.RIGHT -> {
                        if (it.reverseLayout) currentPage - 2 else currentPage
                    }
                    Eye.LEFT -> {
                        if (it.reverseLayout) currentPage else currentPage - 2
                    }
                }, 0)
            }

            eyeTime = System.currentTimeMillis() + 500
        }
    }

    private fun toggleCamera() {
        val eyes = this@ReaderActivity.eye_card
        when (camera) {
            null -> {
                reader_fab_auto.labelText = getString(R.string.reader_fab_auto_cancel)
                reader_fab_auto.setImageResource(R.drawable.eye_off_white)
                eyes.apply {
                    visibility = View.VISIBLE
                    TranslateAnimation(0F, 0F, -100F, 0F).apply {
                        duration = 500
                        fillAfter = false
                        interpolator = OvershootInterpolator()
                    }.let { startAnimation(it) }
                }
                startCamera(this, cameraCallback)
                cameraEnabled = true
            }
            else -> {
                reader_fab_auto.labelText = getString(R.string.reader_fab_auto)
                reader_fab_auto.setImageResource(R.drawable.eye_white)
                eyes.apply {
                    TranslateAnimation(0F, 0F, 0F, -100F).apply {
                        duration = 500
                        fillAfter = false
                        interpolator = AnticipateInterpolator()
                        setAnimationListener(object: Animation.AnimationListener {
                            override fun onAnimationStart(p0: Animation?) {}
                            override fun onAnimationRepeat(p0: Animation?) {}

                            override fun onAnimationEnd(p0: Animation?) {
                                eyes.visibility = View.GONE
                            }
                        })
                    }.let { startAnimation(it) }
                }
                closeCamera()
                cameraEnabled = false
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).onTrimMemory(level)
    }
}