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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.FloatingSearchViewDayNight
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.arlib.floatingsearchview.util.view.SearchInputView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_content.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.doSearch
import xyz.quaver.hitomi.getGalleryIDsFromNozomi
import xyz.quaver.hitomi.getSuggestionsForQuery
import xyz.quaver.pupil.*
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialogFragment
import xyz.quaver.pupil.ui.dialog.GalleryDialog
import xyz.quaver.pupil.util.*
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity :
    BaseActivity(),
    FloatingSearchView.OnMenuItemClickListener,
    NavigationView.OnNavigationItemSelectedListener
{

    enum class Mode {
        SEARCH,
        HISTORY,
        DOWNLOAD,
        FAVORITE
    }

    enum class SortMode {
        NEWEST,
        POPULAR
    }

    private val galleries = ArrayList<Int>()

    private var query = ""
    set(value) {
        field = value
        with(findViewById<SearchInputView>(R.id.search_bar_text)) {
            if (text.toString() != value)
                setText(query, TextView.BufferType.EDITABLE)
        }
    }
    private var queryStack = mutableListOf<String>()

    private var mode = Mode.SEARCH
    private var sortMode = SortMode.NEWEST

    private var galleryIDs: Deferred<List<Int>>? = null
    private var totalItems = 0
    private var loadingJob: Job? = null
    private var currentPage = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                restore(url,
                    onFailure = {
                        Snackbar.make(this.main_recyclerview, R.string.settings_backup_failed, Snackbar.LENGTH_LONG).show()
                    }, onSuccess = {
                        Snackbar.make(this.main_recyclerview, getString(R.string.settings_restore_success, it.size), Snackbar.LENGTH_LONG).show()
                    }
                )
            }
        }

        setContentView(R.layout.activity_main)

        if (Preferences["download_folder", ""].isEmpty())
            DownloadLocationDialogFragment().show(supportFragmentManager, "Download Location Dialog")

        checkUpdate(this)

        initView()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun onBackPressed() {
        when {
            main_drawer_layout.isDrawerOpen(GravityCompat.START) -> main_drawer_layout.closeDrawer(GravityCompat.START)
            queryStack.removeLastOrNull() != null && queryStack.isNotEmpty() -> runOnUiThread {
                query = queryStack.last()

                cancelFetch()
                clearGalleries()
                fetchGalleries(query, sortMode)
                loadBlocks()
            }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        (main_recyclerview?.adapter as? GalleryBlockAdapter)?.timer?.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val perPage = Preferences["per_page", "25"].toInt()
        val maxPage = ceil(totalItems / perPage.toDouble()).roundToInt()

        return when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (currentPage > 0) {
                    runOnUiThread {
                        currentPage--

                        cancelFetch()
                        clearGalleries()
                        fetchGalleries(query, sortMode)
                        loadBlocks()
                    }
                }

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentPage < maxPage) {
                    runOnUiThread {
                        currentPage++

                        cancelFetch()
                        clearGalleries()
                        fetchGalleries(query, sortMode)
                        loadBlocks()
                    }
                }

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            R.id.request_settings.normalizeID() -> {
                runOnUiThread {
                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun initView() {
        var prevP1 = 0
        main_appbar_layout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, p1 ->
                main_searchview.translationY = p1.toFloat()
                main_recyclerview.scrollBy(0, prevP1 - p1)

                with(main_fab) {
                    if (prevP1 > p1)
                        hideMenuButton(true)
                    else if (prevP1 < p1)
                        showMenuButton(true)
                }

                prevP1 = p1
            }
        )

        //NavigationView
        main_nav_view.setNavigationItemSelectedListener(this)

        with(main_fab_cancel) {
            setImageResource(R.drawable.cancel)
            setOnClickListener {
                DownloadService.cancel(this@MainActivity)
            }
        }

        with(main_fab_jump) {
            setImageResource(R.drawable.ic_jump)
            setOnClickListener {
                val perPage = Preferences["per_page", "25"].toInt()
                val editText = EditText(context)

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_jump_title)
                    setMessage(getString(
                        R.string.main_jump_message,
                        currentPage+1,
                        ceil(totalItems / perPage.toDouble()).roundToInt()
                    ))

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        currentPage = (editText.text.toString().toIntOrNull() ?: return@setPositiveButton)-1

                        runOnUiThread {
                            cancelFetch()
                            clearGalleries()
                            loadBlocks()
                        }
                    }
                }.show()
            }
        }

        with(main_fab_random) {
            setImageResource(R.drawable.shuffle_variant)
            setOnClickListener {
                runBlocking {
                    withTimeoutOrNull(100) {
                        galleryIDs?.await()
                    }
                }.let {
                    if (it?.isEmpty() == false) {
                        val galleryID = it.random()

                        GalleryDialog(
                            this@MainActivity,
                            Glide.with(this@MainActivity),
                            galleryID
                        ).apply {
                            onChipClickedHandler.add {
                                runOnUiThread {
                                    query = it.toQuery()
                                    currentPage = 0

                                    cancelFetch()
                                    clearGalleries()
                                    fetchGalleries(query, sortMode)
                                    loadBlocks()
                                }
                                dismiss()
                            }
                        }.show()
                    }
                }
            }
        }

        with(main_fab_id) {
            setImageResource(R.drawable.numeric)
            setOnClickListener {
                val editText = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                }

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_open_gallery_by_id)

                    setPositiveButton(android.R.string.ok) { _, _ ->
                            val galleryID = editText.text.toString().toIntOrNull() ?: return@setPositiveButton
                            val intent = Intent(this@MainActivity, ReaderActivity::class.java).apply {
                                putExtra("galleryID", galleryID)
                            }

                            startActivity(intent)
                    }
                }.show()
            }
        }

        setupSearchBar()
        setupRecyclerView()
        fetchGalleries(query, sortMode)
        loadBlocks()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        with(main_recyclerview) {
            adapter = GalleryBlockAdapter(Glide.with(this@MainActivity), galleries).apply {
                onChipClickedHandler.add {
                    runOnUiThread {
                        query = it.toQuery()
                        currentPage = 0

                        cancelFetch()
                        clearGalleries()
                        fetchGalleries(query, sortMode)
                        loadBlocks()
                    }
                }
                onDownloadClickedHandler = { position ->
                    val galleryID = galleries[position]
                    if (Preferences["cache_disable"])
                        Toast.makeText(context, R.string.settings_download_when_cache_disable_warning, Toast.LENGTH_SHORT).show()
                    else {
                        if (DownloadManager.getInstance(context).isDownloading(galleryID)) {     //download in progress
                            DownloadService.cancel(this@MainActivity, galleryID)
                        }
                        else {
                            DownloadManager.getInstance(context).addDownloadFolder(galleryID)
                            DownloadService.download(this@MainActivity, galleryID)
                        }
                    }

                    closeAllItems()
                }

                onDeleteClickedHandler = { position ->
                    val galleryID = galleries[position]
                    DownloadService.delete(this@MainActivity, galleryID)

                    histories.remove(galleryID)

                    if (this@MainActivity.mode != Mode.SEARCH)
                        runOnUiThread {
                            cancelFetch()
                            clearGalleries()
                            fetchGalleries(query, sortMode)
                            loadBlocks()
                        }

                    completeFlag.put(galleryID, false)

                    closeAllItems()
                }
            }
            ItemClickSupport.addTo(this).apply {
                onItemClickListener = listener@{ _, position, v ->
                    if (v !is CardView)
                        return@listener

                    val intent = Intent(this@MainActivity, ReaderActivity::class.java)
                    intent.putExtra("galleryID", galleries[position])

                    //TODO: Maybe sprinkling some transitions will be nice :D
                    startActivity(intent)
                }

                onItemLongClickListener = listener@{ _, position, v ->
                    if (v !is CardView)
                        return@listener false

                    val galleryID = galleries[position]

                    GalleryDialog(
                        this@MainActivity,
                        Glide.with(this@MainActivity),
                        galleryID
                    ).apply {
                        onChipClickedHandler.add {
                            runOnUiThread {
                                query = it.toQuery()
                                currentPage = 0

                                cancelFetch()
                                clearGalleries()
                                fetchGalleries(query, sortMode)
                                loadBlocks()
                            }
                            dismiss()
                        }
                    }.show()

                    true
                }
            }

            var origin = 0f
            var target = -1
            val perPage = Preferences["per_page", "25"].toInt()
            setOnTouchListener { _, event ->
                when(event.action) {
                    MotionEvent.ACTION_UP -> {
                        origin = 0f

                        with(main_recyclerview.adapter as GalleryBlockAdapter) {
                            if(showPrev) {
                                showPrev = false

                                val prev = main_recyclerview.layoutManager?.getChildAt(0)

                                if (prev is LinearLayout) {
                                    val icon = prev.findViewById<ImageView>(R.id.icon_prev)
                                    prev.layoutParams.height = 1
                                    icon.layoutParams.height = 1
                                    icon.rotation = 180f
                                }

                                prev?.requestLayout()

                                notifyItemRemoved(0)
                            }

                            if(showNext) {
                                showNext = false

                                val next = main_recyclerview.layoutManager?.let {
                                    getChildAt(childCount-1)
                                }

                                if (next is LinearLayout) {
                                    val icon = next.findViewById<ImageView>(R.id.icon_next)
                                    next.layoutParams.height = 1
                                    icon.layoutParams.height = 1
                                    icon.rotation = 0f
                                }

                                next?.requestLayout()

                                notifyItemRemoved(itemCount)
                            }
                        }

                        if (target != -1) {
                            currentPage = target

                            runOnUiThread {
                                cancelFetch()
                                clearGalleries()
                                loadBlocks()
                            }

                            target = -1
                        }
                    }
                    MotionEvent.ACTION_DOWN -> origin = event.y
                    MotionEvent.ACTION_MOVE -> {
                        if (origin == 0f)
                            origin = event.y

                        val dist = event.y - origin

                        when {
                            !canScrollVertically(-1) -> {
                                //TOP

                                //Scrolling UP
                                if (dist > 0 && currentPage != 0) {
                                    with(main_recyclerview.adapter as GalleryBlockAdapter) {
                                        if(!showPrev) {
                                            showPrev = true
                                            notifyItemInserted(0)
                                        }
                                    }

                                    val prev = main_recyclerview.layoutManager?.getChildAt(0)

                                    if (prev is LinearLayout) {
                                        val icon = prev.findViewById<ImageView>(R.id.icon_prev)
                                        val text = prev.findViewById<TextView>(R.id.text_prev).apply {
                                            text = getString(R.string.main_move, currentPage)
                                        }
                                        if (dist < 360) {
                                            prev.layoutParams.height = (dist/2).roundToInt()
                                            icon.layoutParams.height = (dist/2).roundToInt()
                                            icon.rotation = dist+180
                                            text.layoutParams.width = dist.roundToInt()

                                            target = -1
                                        }
                                        else {
                                            prev.layoutParams.height = 180
                                            icon.layoutParams.height = 180
                                            icon.rotation = 180f
                                            text.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT

                                            target = currentPage-1
                                        }
                                    }

                                    prev?.requestLayout()

                                    return@setOnTouchListener true
                                } else {
                                    with(main_recyclerview.adapter as GalleryBlockAdapter) {
                                        if(showPrev) {
                                            showPrev = false

                                            val prev = main_recyclerview.layoutManager?.getChildAt(0)

                                            if (prev is LinearLayout) {
                                                val icon = prev.findViewById<ImageView>(R.id.icon_prev)
                                                prev.layoutParams.height = 1
                                                icon.layoutParams.height = 1
                                                icon.rotation = 180f
                                            }

                                            prev?.requestLayout()

                                            notifyItemRemoved(0)
                                        }
                                    }
                                }
                            }
                            !canScrollVertically(1) -> {
                                //BOTTOM

                                //Scrolling DOWN
                                if (dist < 0 && currentPage != ceil(totalItems.toDouble()/perPage).roundToInt()-1) {
                                    with(main_recyclerview.adapter as GalleryBlockAdapter) {
                                        if(!showNext) {
                                            showNext = true
                                            notifyItemInserted(itemCount-1)
                                        }
                                    }

                                    val next = main_recyclerview.layoutManager?.let {
                                        getChildAt(childCount-1)
                                    }

                                    val absDist = abs(dist)

                                    if (next is LinearLayout) {
                                        val icon = next.findViewById<ImageView>(R.id.icon_next)
                                        val text = next.findViewById<TextView>(R.id.text_next).apply {
                                            text = getString(R.string.main_move, currentPage+2)
                                        }

                                        if (absDist < 360) {
                                            next.layoutParams.height = (absDist/2).roundToInt()
                                            icon.layoutParams.height = (absDist/2).roundToInt()
                                            icon.rotation = -absDist
                                            text.layoutParams.width = absDist.roundToInt()

                                            target = -1
                                        } else {
                                            next.layoutParams.height = 180
                                            icon.layoutParams.height = 180
                                            icon.rotation = 0f
                                            text.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT

                                            target = currentPage+1
                                        }
                                    }

                                    next?.requestLayout()

                                    return@setOnTouchListener true
                                } else {
                                    with(main_recyclerview.adapter as GalleryBlockAdapter) {
                                        if(showNext) {
                                            showNext = false

                                            val next = main_recyclerview.layoutManager?.let {
                                                getChildAt(childCount-1)
                                            }

                                            if (next is LinearLayout) {
                                                val icon = next.findViewById<ImageView>(R.id.icon_next)
                                                next.layoutParams.height = 1
                                                icon.layoutParams.height = 1
                                                icon.rotation = 180f
                                            }

                                            next?.requestLayout()

                                            notifyItemRemoved(itemCount)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                false
            }
        }
    }

    private var isFavorite = false
    private val defaultSuggestions: List<SearchSuggestion>
        get() = when {
            isFavorite -> {
                favoriteTags.map {
                    TagSuggestion(it.tag, -1, "", it.area ?: "tag")
                } + FavoriteHistorySwitch(getString(R.string.search_show_histories))
            }
            else -> {
                searchHistory.map {
                    Suggestion(it)
                }.takeLast(20) + FavoriteHistorySwitch(getString(R.string.search_show_tags))
            }
        }.reversed()

    private var suggestionJob : Job? = null
    private fun setupSearchBar() {
        with(main_searchview as FloatingSearchViewDayNight) {
            setOnLeftMenuClickListener(object: FloatingSearchView.OnLeftMenuClickListener {
                override fun onMenuOpened() {
                    (this@MainActivity.main_recyclerview.adapter as GalleryBlockAdapter).closeAllItems()
                }

                override fun onMenuClosed() {
                    //Do Nothing
                }
            })

            onHistoryDeleteClickedListener = {
                searchHistory.remove(it)
                swapSuggestions(defaultSuggestions)
            }
            onFavoriteHistorySwitchClickListener = {
                isFavorite = !isFavorite
                swapSuggestions(defaultSuggestions)
            }

            setOnMenuItemClickListener(this@MainActivity)

            setOnQueryChangeListener { _, query ->
                this@MainActivity.query = query

                suggestionJob?.cancel()

                if (query.isEmpty() or query.endsWith(' ')) {
                    swapSuggestions(defaultSuggestions)

                    return@setOnQueryChangeListener
                }

                swapSuggestions(listOf(LoadingSuggestion(getText(R.string.reader_loading).toString())))

                val currentQuery = query.split(" ").last().replace('_', ' ')

                suggestionJob = CoroutineScope(Dispatchers.IO).launch {
                    val suggestions = kotlin.runCatching {
                        getSuggestionsForQuery(currentQuery).map { TagSuggestion(it) }.toMutableList()
                    }.getOrElse { mutableListOf() }

                    suggestions.filter {
                        val tag = "${it.n}:${it.s.replace(Regex("\\s"), "_")}"
                        favoriteTags.contains(Tag.parse(tag))
                    }.reversed().forEach {
                        suggestions.remove(it)
                        suggestions.add(0, it)
                    }

                    withContext(Dispatchers.Main) {
                        swapSuggestions(if (suggestions.isNotEmpty()) suggestions else listOf(NoResultSuggestion(getText(R.string.main_no_result).toString())))
                    }
                }
            }

            setOnFocusChangeListener(object: FloatingSearchView.OnFocusChangeListener {
                override fun onFocus() {
                    if (query.isEmpty() or query.endsWith(' '))
                        swapSuggestions(defaultSuggestions)
                }

                override fun onFocusCleared() {
                    suggestionJob?.cancel()

                    runOnUiThread {
                        cancelFetch()
                        clearGalleries()
                        currentPage = 0
                        fetchGalleries(query, sortMode)
                        loadBlocks()
                    }
                }
            })

            attachNavigationDrawerToMenuButton(main_drawer_layout)
        }
    }

    override fun onActionMenuItemSelected(item: MenuItem?) {
        when(item?.itemId) {
            R.id.main_menu_settings -> startActivityForResult(Intent(this@MainActivity, SettingsActivity::class.java), R.id.request_settings.normalizeID())
            R.id.main_menu_thin -> {
                main_recyclerview.apply {
                    (adapter as GalleryBlockAdapter).apply {
                        isThin = !isThin
                    }

                    adapter = adapter       // Force to redraw
                }
            }
            R.id.main_menu_sort_newest -> {
                sortMode = SortMode.NEWEST
                item.isChecked = true

                runOnUiThread {
                    currentPage = 0

                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
            }
            R.id.main_menu_sort_popular -> {
                sortMode = SortMode.POPULAR
                item.isChecked = true

                runOnUiThread {
                    currentPage = 0

                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        runOnUiThread {
            main_drawer_layout.closeDrawers()

            when(item.itemId) {
                R.id.main_drawer_home -> {
                    cancelFetch()
                    clearGalleries()
                    currentPage = 0
                    query = ""
                    queryStack.clear()
                    mode = Mode.SEARCH
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
                R.id.main_drawer_history -> {
                    cancelFetch()
                    clearGalleries()
                    currentPage = 0
                    query = ""
                    queryStack.clear()
                    mode = Mode.HISTORY
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
                R.id.main_drawer_downloads -> {
                    cancelFetch()
                    clearGalleries()
                    currentPage = 0
                    query = ""
                    queryStack.clear()
                    mode = Mode.DOWNLOAD
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
                R.id.main_drawer_favorite -> {
                    cancelFetch()
                    clearGalleries()
                    currentPage = 0
                    query = ""
                    queryStack.clear()
                    mode = Mode.FAVORITE
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
                R.id.main_drawer_help -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help))))
                }
                R.id.main_drawer_github -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github))))
                }
                R.id.main_drawer_homepage -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page))))
                }
                R.id.main_drawer_email -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.email))))
                }
                R.id.main_drawer_kakaotalk -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discord))))
                }
            }
        }

        return true
    }

    private fun cancelFetch() {
        galleryIDs?.cancel()
        loadingJob?.cancel()
    }

    private fun clearGalleries() {
        galleries.clear()

        with(main_recyclerview.adapter as GalleryBlockAdapter?) {
            this ?: return@with

            this.completeFlag.clear()
            this.notifyDataSetChanged()
        }

        main_appbar_layout.setExpanded(true)
        main_noresult.visibility = View.INVISIBLE
        main_progressbar.show()
    }

    private fun fetchGalleries(query: String, sortMode: SortMode) {
        val defaultQuery: String = Preferences["default_query"]

        if (query.isNotBlank())
            searchHistory.add(query)

        if (query != queryStack.lastOrNull()) {
            queryStack.remove(query)
            queryStack.add(query)
        }

        if (query.isNotEmpty() && mode != Mode.SEARCH) {
            Snackbar.make(this@MainActivity.main_recyclerview, R.string.search_all, Snackbar.LENGTH_SHORT).apply {
                setAction(android.R.string.ok) {
                    cancelFetch()
                    clearGalleries()
                    currentPage = 0
                    mode = Mode.SEARCH
                    queryStack.clear()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
            }.show()
        }

        galleryIDs = null

        if (galleryIDs?.isActive == true)
            return

        galleryIDs = CoroutineScope(Dispatchers.IO).async {
            when(mode) {
                Mode.SEARCH -> {
                    when {
                        query.isEmpty() and defaultQuery.isEmpty() -> {
                            when(sortMode) {
                                SortMode.POPULAR -> getGalleryIDsFromNozomi(null, "popular", "all")
                                else -> getGalleryIDsFromNozomi(null, "index", "all")
                            }.also {
                                totalItems = it.size
                            }
                        }
                        else -> doSearch("$defaultQuery $query", sortMode == SortMode.POPULAR).also {
                            totalItems = it.size
                        }
                    }
                }
                Mode.HISTORY -> {
                    when {
                        query.isEmpty() -> {
                            histories.reversed().also {
                                totalItems = it.size
                            }
                        }
                        else -> {
                            val result = doSearch(query).sorted()
                            histories.reversed().filter { result.binarySearch(it) >= 0 }.also {
                                totalItems = it.size
                            }
                        }
                    }
                }
                Mode.DOWNLOAD -> {
                    val downloads = DownloadManager.getInstance(this@MainActivity).downloadFolderMap.keys.toList()

                    when {
                        query.isEmpty() -> downloads.reversed().also {
                            totalItems = it.size
                        }
                        else -> {
                            val result = doSearch(query).sorted()
                            downloads.reversed().filter { result.binarySearch(it) >= 0 }.also {
                                totalItems = it.size
                            }
                        }
                    }
                }
                Mode.FAVORITE -> {
                    when {
                        query.isEmpty() -> favorites.reversed().also {
                            totalItems = it.size
                        }
                        else -> {
                            val result = doSearch(query).sorted()
                            favorites.reversed().filter { result.binarySearch(it) >= 0 }.also {
                                totalItems = it.size
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadBlocks() {
        val perPage = Preferences["per_page", "25"].toInt()

        loadingJob = CoroutineScope(Dispatchers.IO).launch {
            val galleryIDs = try {
                galleryIDs!!.await().also {
                    if (it.isEmpty())
                        throw Exception("No result")
                }
            } catch (e: Exception) {

                if (e.message != "No result")
                    FirebaseCrashlytics.getInstance().recordException(e)

                withContext(Dispatchers.Main) {
                    main_noresult.visibility = View.VISIBLE
                    main_progressbar.hide()
                }

                return@launch
            }

            galleryIDs.slice(currentPage*perPage until min(currentPage*perPage+perPage, galleryIDs.size)).chunked(5).let { chunks ->
                for (chunk in chunks)
                    chunk.map { galleryID ->
                        async {
                            Cache.getInstance(this@MainActivity, galleryID).getGalleryBlock()?.let {
                                galleryID
                            }
                        }
                    }.forEach {
                        it.await()?.also {
                            withContext(Dispatchers.Main) {
                                main_progressbar.hide()

                                galleries.add(it)
                                main_recyclerview.adapter!!.notifyItemInserted(galleries.size - 1)
                            }
                        }
                    }
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
