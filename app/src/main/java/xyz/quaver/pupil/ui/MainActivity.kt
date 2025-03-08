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
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.floatingsearchview.util.view.MenuView
import xyz.quaver.floatingsearchview.util.view.SearchInputView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.databinding.MainActivityBinding
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.histories
import xyz.quaver.pupil.hitomi.SortMode
import xyz.quaver.pupil.hitomi.doSearch
import xyz.quaver.pupil.hitomi.getSuggestionsForQuery
import xyz.quaver.pupil.searchHistory
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.types.FavoriteHistorySwitch
import xyz.quaver.pupil.types.LoadingSuggestion
import xyz.quaver.pupil.types.NoResultSuggestion
import xyz.quaver.pupil.types.Suggestion
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.types.TagSuggestion
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialogFragment
import xyz.quaver.pupil.ui.dialog.GalleryDialog
import xyz.quaver.pupil.ui.view.MainView
import xyz.quaver.pupil.ui.view.ProgressCard
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.checkUpdate
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.requestNotificationPermission
import xyz.quaver.pupil.util.restore
import xyz.quaver.pupil.util.showNotificationPermissionExplanationDialog
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity :
    BaseActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    enum class Mode {
        SEARCH,
        HISTORY,
        DOWNLOAD,
        FAVORITE
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
    private var sortMode = SortMode.DATE_ADDED

    private var galleryIDs: Deferred<List<Int>>? = null
    private var totalItems = 0
    private var loadingJob: Job? = null
    private var currentPage = 0

    private lateinit var binding: MainActivityBinding

    private val requestNotificationPermssionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                showNotificationPermissionExplanationDialog(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                restore(url,
                    onFailure = {
                        Snackbar.make(
                            binding.contents.recyclerview,
                            R.string.settings_backup_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }, onSuccess = {
                        Snackbar.make(
                            binding.contents.recyclerview,
                            getString(R.string.settings_restore_success, it),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        requestNotificationPermission(this, requestNotificationPermssionLauncher, false) {}

        if (Preferences["download_folder", ""].isEmpty())
            DownloadLocationDialogFragment().show(
                supportFragmentManager,
                "Download Location Dialog"
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Preferences["download_folder_ignore_warning", false] &&
            ContextCompat.getExternalFilesDirs(this, null).filterNotNull()
                .map { Uri.fromFile(it).toString() }
                .contains(Preferences["download_folder", ""])
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.unaccessible_download_folder)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DownloadLocationDialogFragment().show(
                        supportFragmentManager,
                        "Download Location Dialog"
                    )
                }.setNegativeButton(R.string.ignore) { _, _ ->
                    Preferences["download_folder_ignore_warning"] = true
                }.show()
        }

        initView()
    }

    override fun onResume() {
        super.onResume()

        checkUpdate(this)
    }

    override fun onBackPressed() {
        when {
            binding.drawer.isDrawerOpen(GravityCompat.START) -> binding.drawer.closeDrawer(
                GravityCompat.START
            )

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

        (binding.contents.recyclerview.adapter as? GalleryBlockAdapter)?.updateAll = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val perPage = Preferences["per_page", "25"].toInt()
        val maxPage = ceil(totalItems / perPage.toDouble()).roundToInt()

        return when (keyCode) {
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

    private fun initView() {
        binding.contents.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // -height of the search view < translationY < 0
                binding.contents.searchview.translationY =
                    min(
                        max(
                            binding.contents.searchview.translationY - dy,
                            -binding.contents.searchview.binding.querySection.root.height.toFloat()
                        ), 0F
                    )

                if (dy > 0)
                    binding.contents.fab.hideMenuButton(true)
                else if (dy < 0)
                    binding.contents.fab.showMenuButton(true)
            }
        })

        Linkify.addLinks(
            binding.contents.noresult,
            Pattern.compile(getString(R.string.https_text)),
            null,
            null,
            { _, _ -> getString(R.string.https) })

        //NavigationView
        binding.navView.setNavigationItemSelectedListener(this)

        with(binding.contents.cancelFab) {
            setImageResource(R.drawable.cancel)
            setOnClickListener {
                DownloadService.cancel(this@MainActivity)
            }
        }

        with(binding.contents.jumpFab) {
            setImageResource(R.drawable.ic_jump)
            setOnClickListener {
                val perPage = Preferences["per_page", "25"].toInt()
                val editText = EditText(context)

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_jump_title)
                    setMessage(
                        getString(
                            R.string.main_jump_message,
                            currentPage + 1,
                            ceil(totalItems / perPage.toDouble()).roundToInt()
                        )
                    )

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        currentPage =
                            (editText.text.toString().toIntOrNull() ?: return@setPositiveButton) - 1

                        runOnUiThread {
                            cancelFetch()
                            clearGalleries()
                            loadBlocks()
                        }
                    }
                }.show()
            }
        }

        with(binding.contents.randomFab) {
            setImageResource(R.drawable.shuffle_variant)
            setOnClickListener {
                runBlocking {
                    withTimeoutOrNull(100) {
                        galleryIDs?.await()
                    }
                }.let {
                    if (it?.isEmpty() == false) {
                        val galleryID = it.random()

                        GalleryDialog(this@MainActivity, galleryID).apply {
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

        with(binding.contents.idFab) {
            setImageResource(R.drawable.numeric)
            setOnClickListener {
                val editText = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                }

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_open_gallery_by_id)

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        val galleryID =
                            editText.text.toString().toIntOrNull() ?: return@setPositiveButton

                        GalleryDialog(this@MainActivity, galleryID).apply {
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
                }.show()
            }
        }

        with(binding.contents.view) {
            setOnPageTurnListener(object : MainView.OnPageTurnListener {
                override fun onPrev(page: Int) {
                    currentPage--

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    ViewCompat.animate(binding.contents.searchview)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .translationY(0F)

                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }

                override fun onNext(page: Int) {
                    currentPage++

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    ViewCompat.animate(binding.contents.searchview)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .translationY(0F)

                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query, sortMode)
                    loadBlocks()
                }
            })
        }

        setupSearchBar()
        setupRecyclerView()
        fetchGalleries(query, sortMode)
        loadBlocks()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        with(binding.contents.recyclerview) {
            adapter = GalleryBlockAdapter(galleries).apply {
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

                    requestNotificationPermission(
                        this@MainActivity,
                        requestNotificationPermssionLauncher
                    ) {
                        if (DownloadManager.getInstance(context)
                                .isDownloading(galleryID)
                        ) {     //download in progress
                            DownloadService.cancel(this@MainActivity, galleryID)
                        } else {
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

                    closeAllItems()
                }
            }
            ItemClickSupport.addTo(this).apply {
                onItemClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCard)
                        return@listener

                    val intent = Intent(this@MainActivity, ReaderActivity::class.java)
                    intent.putExtra("galleryID", galleries[position])

                    //TODO: Maybe sprinkling some transitions will be nice :D
                    startActivity(intent)
                }

                onItemLongClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCard)
                        return@listener false

                    val galleryID = galleries.getOrNull(position) ?: return@listener true

                    GalleryDialog(this@MainActivity, galleryID).apply {
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
                }.takeLast(10) + FavoriteHistorySwitch(getString(R.string.search_show_tags))
            }
        }.reversed()

    private var suggestionJob: Job? = null
    private fun setupSearchBar() {
        with(binding.contents.searchview) {
            val scrollSuggestionToTop = {
                with(binding.suggestionSection.suggestionsList) {
                    MainScope().launch {
                        withTimeout(1000) {
                            val layoutManager = layoutManager as LinearLayoutManager
                            while (layoutManager.findLastVisibleItemPosition() != adapter?.itemCount?.minus(
                                    1
                                )
                            ) {
                                layoutManager.scrollToPosition(adapter?.itemCount?.minus(1) ?: 0)
                                delay(100)
                            }
                        }
                    }
                }
            }

            onMenuStatusChangeListener = object : FloatingSearchView.OnMenuStatusChangeListener {
                override fun onMenuOpened() {
                    (this@MainActivity.binding.contents.recyclerview.adapter as GalleryBlockAdapter).closeAllItems()
                }

                override fun onMenuClosed() {
                    //Do Nothing
                }
            }

            post {
                findViewById<MenuView>(R.id.menu_view).menuItems.firstOrNull {
                    (it as MenuItem).itemId == R.id.main_menu_thin
                }?.let {
                    (it as MenuItem).isChecked = Preferences["thin"]
                }
            }

            onHistoryDeleteClickedListener = {
                searchHistory.remove(it)
                swapSuggestions(defaultSuggestions)
            }
            onFavoriteHistorySwitchClickListener = {
                isFavorite = !isFavorite
                swapSuggestions(defaultSuggestions)
                scrollSuggestionToTop()
            }

            onMenuItemClickListener = {
                onActionMenuItemSelected(it)
            }

            onQueryChangeListener = lambda@{ _, query ->
                this@MainActivity.query = query

                suggestionJob?.cancel()

                if (query.isEmpty() or query.endsWith(' ')) {
                    swapSuggestions(defaultSuggestions)
                    scrollSuggestionToTop()

                    return@lambda
                }

                swapSuggestions(listOf(LoadingSuggestion(getText(R.string.reader_loading).toString())))

                val currentQuery = query.split(" ").last()
                    .replace(Regex("^-"), "")
                    .replace('_', ' ')

                suggestionJob = CoroutineScope(Dispatchers.IO).launch {
                    val suggestions = kotlin.runCatching {
                        getSuggestionsForQuery(currentQuery).map { TagSuggestion(it) }
                            .toMutableList()
                    }.getOrElse { mutableListOf() }

                    suggestions.filter {
                        val tag = "${it.n}:${it.s.replace(Regex("\\s"), "_")}"
                        favoriteTags.contains(Tag.parse(tag))
                    }.reversed().forEach {
                        suggestions.remove(it)
                        suggestions.add(0, it)
                    }

                    withContext(Dispatchers.Main) {
                        swapSuggestions(
                            if (suggestions.isNotEmpty()) suggestions else listOf(
                                NoResultSuggestion(getText(R.string.main_no_result).toString())
                            )
                        )
                    }
                }
            }

            onFocusChangeListener = object : FloatingSearchView.OnFocusChangeListener {
                override fun onFocus() {
                    if (query.isEmpty() or query.endsWith(' ')) {
                        swapSuggestions(defaultSuggestions)
                        scrollSuggestionToTop()
                    }
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
            }

            attachNavigationDrawerToMenuButton(this@MainActivity.binding.drawer)
        }
    }

    fun onActionMenuItemSelected(item: MenuItem?) {
        when (item?.itemId) {
            R.id.main_menu_settings -> startActivity(
                Intent(
                    this@MainActivity,
                    SettingsActivity::class.java
                )
            )

            R.id.main_menu_thin -> {
                val thin = !item.isChecked

                item.isChecked = thin
                binding.contents.recyclerview.apply {
                    (adapter as GalleryBlockAdapter).apply {
                        this.thin = thin

                        Preferences["thin"] = thin
                    }

                    adapter = adapter       // Force to redraw
                }
            }

            R.id.main_menu_sort_date_added,
            R.id.main_menu_sort_date_published,
            R.id.main_menu_sort_popular_today,
            R.id.main_menu_sort_popular_week,
            R.id.main_menu_sort_popular_month,
            R.id.main_menu_sort_popular_year,
            R.id.main_menu_sort_random -> {
                sortMode = sortModeLookup[item.itemId]!!
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
            binding.drawer.closeDrawers()

            when (item.itemId) {
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
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.home_page))
                        )
                    )
                }

                R.id.main_drawer_email -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.email))))
                }

                R.id.main_drawer_kakaotalk -> {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.discord))
                        )
                    )
                }
            }
        }

        return true
    }

    private fun cancelFetch() {
        galleryIDs?.cancel()
        loadingJob?.cancel()
    }

    private fun clearGalleries() = CoroutineScope(Dispatchers.Main).launch {
        galleries.clear()

        with(binding.contents.recyclerview.adapter as GalleryBlockAdapter?) {
            this ?: return@with

            this.notifyDataSetChanged()
        }

        binding.contents.noresult.visibility = View.INVISIBLE
        binding.contents.progressbar.show()
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
            Snackbar.make(binding.contents.recyclerview, R.string.search_all, Snackbar.LENGTH_SHORT)
                .apply {
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
            when (mode) {
                Mode.SEARCH -> {
                    doSearch(
                        "$defaultQuery $query",
                        sortMode
                    ).also {
                        totalItems = it.size
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
                            val result = doSearch(query, SortMode.DATE_ADDED).sorted()
                            histories.reversed().filter { result.binarySearch(it) >= 0 }.also {
                                totalItems = it.size
                            }
                        }
                    }
                }

                Mode.DOWNLOAD -> {
                    val downloads =
                        DownloadManager.getInstance(this@MainActivity).downloadFolderMap.keys.toList()

                    when {
                        query.isEmpty() -> downloads.reversed().also {
                            totalItems = it.size
                        }

                        else -> {
                            val result = doSearch(query, SortMode.DATE_ADDED).sorted()
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
                            val result = doSearch(query, SortMode.DATE_ADDED).sorted()
                            favorites.reversed().filter { result.binarySearch(it) >= 0 }.also {
                                totalItems = it.size
                            }
                        }
                    }
                }
            }.toList()
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
                if (e !is CancellationException)
                    FirebaseCrashlytics.getInstance().recordException(e)

                withContext(Dispatchers.Main) {
                    binding.contents.noresult.visibility = View.VISIBLE
                    binding.contents.progressbar.hide()
                }

                return@launch
            }

            launch(Dispatchers.Main) {
                binding.contents.view.setCurrentPage(
                    currentPage + 1,
                    galleryIDs.size > (currentPage + 1) * perPage
                )
            }

            galleryIDs.slice(
                currentPage * perPage until min(
                    currentPage * perPage + perPage,
                    galleryIDs.size
                )
            ).chunked(5).let { chunks ->
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
                                binding.contents.progressbar.hide()

                                galleries.add(it)
                                binding.contents.recyclerview.adapter!!.notifyItemInserted(galleries.size - 1)
                            }
                        }
                    }
            }
        }
    }
}
