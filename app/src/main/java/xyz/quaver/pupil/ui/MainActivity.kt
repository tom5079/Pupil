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
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.floatingsearchview.util.view.MenuView
import xyz.quaver.floatingsearchview.util.view.SearchInputView
import xyz.quaver.hitomi.getSuggestionsForQuery
import xyz.quaver.pupil.*
import xyz.quaver.pupil.adapters.SearchResultsAdapter
import xyz.quaver.pupil.databinding.MainActivityBinding
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.hitomi.Hitomi
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialogFragment
import xyz.quaver.pupil.ui.dialog.GalleryDialog
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.ui.view.SwipePageTurnView
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.checkUpdate
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.restore
import java.util.regex.Pattern
import kotlin.math.*

class MainActivity :
    BaseActivity(),
    NavigationView.OnNavigationItemSelectedListener
{
    private val searchResults = mutableListOf<SearchResult>()

    private var query = ""
    set(value) {
        field = value
        with(findViewById<SearchInputView>(R.id.search_bar_text)) {
            if (text.toString() != value)
                setText(query, TextView.BufferType.EDITABLE)
        }
    }
    private var queryStack = mutableListOf<String>()

    @Suppress("UNCHECKED_CAST")
    private var source: Source<Enum<*>, SearchResult> = Hitomi() as Source<Enum<*>, SearchResult>
    private var sortMode = Hitomi.SortMode.NEWEST

    private var searchJob: Deferred<Pair<List<SearchResult>, Int>>? = null
    private var totalItems = 0
    private var currentPage = 1

    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                restore(url,
                    onFailure = {
                        Snackbar.make(binding.contents.recyclerview, R.string.settings_backup_failed, Snackbar.LENGTH_LONG).show()
                    }, onSuccess = {
                        Snackbar.make(binding.contents.recyclerview, getString(R.string.settings_restore_success, it.size), Snackbar.LENGTH_LONG).show()
                    }
                )
            }
        }

        if (Preferences["download_folder", ""].isEmpty())
            DownloadLocationDialogFragment().show(supportFragmentManager, "Download Location Dialog")

        checkUpdate(this)

        initView()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun onBackPressed() {
        when {
            binding.drawer.isDrawerOpen(GravityCompat.START) -> binding.drawer.closeDrawer(GravityCompat.START)
            queryStack.removeLastOrNull() != null && queryStack.isNotEmpty() -> runOnUiThread {
                query = queryStack.last()

                query()
            }
            else -> super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val perPage = Preferences["per_page", "25"].toInt()
        val maxPage = ceil(totalItems / perPage.toDouble()).roundToInt()

        return when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (currentPage > 1) {
                    runOnUiThread {
                        currentPage--

                        query()
                    }
                }

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentPage < maxPage) {
                    runOnUiThread {
                        currentPage++

                        query()
                    }
                }

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun initView() {
        binding.contents.recyclerview.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // -height of the search view < translationY < 0
                binding.contents.searchview.translationY =
                    min(
                        max(
                        binding.contents.searchview.translationY - dy,
                        -binding.contents.searchview.findViewById<CardView>(R.id.search_query_section).height.toFloat()
                    ), 0F)

                if (dy > 0)
                    binding.contents.fab.hideMenuButton(true)
                else if (dy < 0)
                    binding.contents.fab.showMenuButton(true)
            }
        })

        Linkify.addLinks(binding.contents.noresult, Pattern.compile(getString(R.string.https_text)), null, null, { _, _ -> getString(R.string.https) })

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
                    setMessage(getString(
                        R.string.main_jump_message,
                        currentPage,
                        ceil(totalItems / perPage.toDouble()).roundToInt()
                    ))

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        currentPage = (editText.text.toString().toIntOrNull() ?: return@setPositiveButton)

                        query()
                    }
                }.show()
            }
        }

        with(binding.contents.randomFab) {
            setImageResource(R.drawable.shuffle_variant)
            setOnClickListener {
                runBlocking {
                    withTimeoutOrNull(100) {
                        searchJob?.await()
                    }
                }.let {
                    if (it?.first?.isEmpty() == false) {
                        val random = it.first.random()

                        GalleryDialog(this@MainActivity, random.id).apply {
                            onChipClickedHandler.add {
                                query = it.toQuery()
                                currentPage = 1

                                query()
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
                        val galleryID = editText.text.toString()

                        GalleryDialog(this@MainActivity, galleryID).apply {
                            onChipClickedHandler.add {
                                query = it.toQuery()
                                currentPage = 1

                                query()
                                dismiss()
                            }
                        }.show()
                    }
                }.show()
            }
        }

        with(binding.contents.swipePageTurnView) {
            setOnPageTurnListener(object: SwipePageTurnView.OnPageTurnListener {
                override fun onPrev(page: Int) {
                    currentPage--

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    ViewCompat.animate(binding.contents.searchview)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .translationY(0F)

                    query()
                }

                override fun onNext(page: Int) {
                    currentPage++

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    ViewCompat.animate(binding.contents.searchview)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .translationY(0F)

                    query()
                }
            })
        }

        setupSearchBar()
        setupRecyclerView()
        query()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        with(binding.contents.recyclerview) {
            adapter = SearchResultsAdapter(searchResults).apply {
                onChipClickedHandler.add {
                    query = it.toQuery()
                    currentPage = 0

                    query()
                }
                onDownloadClickedHandler = { id ->
                    if (DownloadManager.getInstance(context).isDownloading(id)) {     //download in progress
                        DownloadService.cancel(this@MainActivity, id)
                    }
                    else {
                        DownloadManager.getInstance(context).addDownloadFolder(id)
                        DownloadService.download(this@MainActivity, id)
                    }

                    closeAllItems()
                }

                onDeleteClickedHandler = { id ->
                    DownloadService.delete(this@MainActivity, id)

                    histories.remove(id)

                    closeAllItems()
                }
            }
            ItemClickSupport.addTo(this).apply {
                onItemClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCardView)
                        return@listener

                    val intent = Intent(this@MainActivity, ReaderActivity::class.java)
                    intent.putExtra("galleryID", searchResults[position].id)

                    //TODO: Maybe sprinkling some transitions will be nice :D
                    startActivity(intent)
                }

                onItemLongClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCardView)
                        return@listener false

                    val result = searchResults.getOrNull(position) ?: return@listener true

                    GalleryDialog(this@MainActivity, result.id).apply {
                        onChipClickedHandler.add {
                            query = it.toQuery()
                            currentPage = 0

                            query()
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

    private var suggestionJob : Job? = null
    private fun setupSearchBar() {
        with(binding.contents.searchview) {
            onMenuStatusChangeListener = object: FloatingSearchView.OnMenuStatusChangeListener {
                override fun onMenuOpened() {
                    (binding.contents.recyclerview.adapter as SearchResultsAdapter).closeAllItems()
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
            }

            onMenuItemClickListener = {
                onActionMenuItemSelected(it)
            }

            onQueryChangeListener = lambda@{ _, query ->
                this@MainActivity.query = query

                suggestionJob?.cancel()

                if (query.isEmpty() or query.endsWith(' ')) {
                    swapSuggestions(defaultSuggestions)

                    return@lambda
                }

                swapSuggestions(listOf(LoadingSuggestion(getText(R.string.reader_loading).toString())))

                val currentQuery = query.split(" ").last()
                    .replace(Regex("^-"), "")
                    .replace('_', ' ')

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

            onFocusChangeListener = object: FloatingSearchView.OnFocusChangeListener {
                override fun onFocus() {
                    if (query.isEmpty() or query.endsWith(' '))
                        swapSuggestions(defaultSuggestions)
                }

                override fun onFocusCleared() {
                    suggestionJob?.cancel()

                    currentPage = 1
                    query()
                }
            }

            attachNavigationDrawerToMenuButton(binding.drawer)
        }
    }

    fun onActionMenuItemSelected(item: MenuItem?) {
        when(item?.itemId) {
            R.id.main_menu_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            R.id.main_menu_thin -> {
                // TODO
            }
            R.id.main_menu_sort_newest -> {
                sortMode = Hitomi.SortMode.NEWEST
                item.isChecked = true

                runOnUiThread {
                    currentPage = 1

                    query()
                }
            }
            R.id.main_menu_sort_popular -> {
                sortMode = Hitomi.SortMode.POPULAR
                item.isChecked = true

                runOnUiThread {
                    currentPage = 1

                    query()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        runOnUiThread {
            binding.drawer.closeDrawers()

            when(item.itemId) {
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
        searchJob?.cancel()
    }

    private fun clearGalleries() = CoroutineScope(Dispatchers.Main).launch {
        searchResults.clear()

        binding.contents.recyclerview.adapter?.notifyDataSetChanged()

        binding.contents.noresult.visibility = View.INVISIBLE
        binding.contents.progressbar.show()
    }
    private fun query() {
        val perPage = Preferences["per_page", "25"].toInt()

        cancelFetch()
        clearGalleries()

        CoroutineScope(Dispatchers.Main).launch {
            searchJob = async(Dispatchers.IO) {
                source.query(
                    query + Preferences["default_query", ""],
                    (currentPage - 1) * perPage until currentPage * perPage,
                    sortMode
                )
            }.also {
                val results: List<SearchResult>

                it.await().let { r ->
                    results = r.first
                    totalItems = r.second
                }

                binding.contents.progressbar.hide()
                binding.contents.swipePageTurnView.setCurrentPage(currentPage, totalItems > currentPage*perPage)

                if (results.isEmpty()) {
                    binding.contents.noresult.visibility = View.VISIBLE
                } else {
                    searchResults.addAll(results)
                    binding.contents.recyclerview.adapter?.notifyDataSetChanged()
                }
            }
        }
    }
}
