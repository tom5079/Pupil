package xyz.quaver.pupil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.*
import android.text.style.AlignmentSpan
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.arlib.floatingsearchview.util.view.SearchInputView
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_content.*
import kotlinx.android.synthetic.main.dialog_galleryblock.view.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.content
import ru.noties.markwon.Markwon
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.types.TagSuggestion
import xyz.quaver.pupil.util.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val galleries = ArrayList<Pair<GalleryBlock, Deferred<String>>>()

    private var query = ""

    private val SETTINGS = 45162

    private var galleryIDs: Deferred<List<Int>>? = null
    private var loadingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Histories.default = Histories(File(cacheDir, "histories.json"))
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_main)

        checkUpdate()

        main_appbar_layout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, p1 ->
                main_searchview.translationY = p1.toFloat()
                main_recyclerview.translationY = p1.toFloat()
            }
        )

        with(main_swipe_layout) {
            setProgressViewOffset(
                false,
                resources.getDimensionPixelSize(R.dimen.progress_view_start),
                resources.getDimensionPixelSize(R.dimen.progress_view_offset)
            )

            setOnRefreshListener {
                CoroutineScope(Dispatchers.Main).launch {
                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query)
                    loadBlocks()
                }
            }
        }

        main_nav_view.setNavigationItemSelectedListener {
            CoroutineScope(Dispatchers.Main).launch {
                main_drawer_layout.closeDrawers()

                when(it.itemId) {
                    R.id.main_drawer_home -> {
                        cancelFetch()
                        clearGalleries()
                        query = query.replace("HISTORY", "")
                        fetchGalleries(query)
                    }
                    R.id.main_drawer_history -> {
                        cancelFetch()
                        clearGalleries()
                        query += "HISTORY"
                        fetchGalleries(query)
                    }
                    R.id.main_drawer_help -> {
                        AlertDialog.Builder(this@MainActivity).apply {
                            title = getString(R.string.help_dialog_title)
                            setMessage(R.string.help_dialog_message)

                            setPositiveButton(android.R.string.ok) { _, _ -> }
                        }.show()
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
                }
                loadBlocks()
            }

            true
        }

        setupSearchBar()
        setupRecyclerView()
        fetchGalleries(query)
        loadBlocks()
    }

    override fun onBackPressed() {
        if (main_drawer_layout.isDrawerOpen(GravityCompat.START))
            main_drawer_layout.closeDrawer(GravityCompat.START)
        else if (query.isNotEmpty()) {
            runOnUiThread {
                query = ""
                findViewById<SearchInputView>(R.id.search_bar_text).setText(query, TextView.BufferType.EDITABLE)

                cancelFetch()
                clearGalleries()
                fetchGalleries(query)
                loadBlocks()
            }
        }
        else
            super.onBackPressed()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            SETTINGS -> {
                runOnUiThread {
                    cancelFetch()
                    clearGalleries()
                    fetchGalleries(query)
                    loadBlocks()
                }
            }
        }
    }

    private fun checkUpdate() {

        fun extractReleaseNote(update: JsonObject, locale: String) : String {
            val markdown = update["body"]!!.content

            val target = when(locale) {
                "ko" -> "한국어"
                "ja" -> "日本語"
                else -> "English"
            }

            val releaseNote = Regex("^# Release Note.+$")
            val language = Regex("^## $target$")
            val end = Regex("^#.+$")

            var releaseNoteFlag = false
            var languageFlag = false

            val result = StringBuilder()

            for(line in markdown.lines()) {
                if (releaseNote.matches(line)) {
                    releaseNoteFlag = true
                    continue
                }

                if (releaseNoteFlag) {
                    if (language.matches(line)) {
                        languageFlag = true
                        continue
                    }
                }

                if (languageFlag) {
                    if (end.matches(line))
                        break

                    result.append(line+"\n")
                }
            }

            return getString(R.string.update_release_note, update["tag_name"]?.content, result.toString())
        }

        CoroutineScope(Dispatchers.Default).launch {
            val update =
                checkUpdate(getString(R.string.release_url), BuildConfig.VERSION_NAME) ?: return@launch

            val dialog = AlertDialog.Builder(this@MainActivity).apply {
                setTitle(R.string.update_title)
                val msg = extractReleaseNote(update, Locale.getDefault().language)
                setMessage(Markwon.create(context).toMarkdown(msg))
                setPositiveButton(android.R.string.yes) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page))))
                }
                setNegativeButton(android.R.string.no) { _, _ ->}
            }

            launch(Dispatchers.Main) {
                dialog.show()
            }
        }
    }

    private fun setupRecyclerView() {
        with(main_recyclerview) {
            adapter = GalleryBlockAdapter(galleries).apply {
                onChipClickedHandler.add {
                    post {
                        query = it.toQuery()
                        this@MainActivity.findViewById<SearchInputView>(R.id.search_bar_text)
                            .setText(query, TextView.BufferType.EDITABLE)

                        cancelFetch()
                        clearGalleries()
                        fetchGalleries(query)
                        loadBlocks()
                    }
                }
            }
            addOnScrollListener(
                object: RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                        if (loadingJob?.isActive != true)
                            if (layoutManager.findLastCompletelyVisibleItemPosition() == galleries.size)
                                loadBlocks()
                    }
                }
            )
            ItemClickSupport.addTo(this)
                .setOnItemClickListener { _, position, _ ->
                    val intent = Intent(this@MainActivity, ReaderActivity::class.java)
                    val gallery = galleries[position].first
                    intent.putExtra("galleryblock", Json(JsonConfiguration.Stable).stringify(GalleryBlock.serializer(), gallery))

                    //TODO: Maybe sprinke some transitions will be nice :D
                    startActivity(intent)

                    Histories.default.add(gallery.id)
                }.setOnItemLongClickListener { recyclerView, position, v ->
                    val galleryBlock = galleries[position].first
                    val view = LayoutInflater.from(this@MainActivity)
                        .inflate(R.layout.dialog_galleryblock, recyclerView, false)

                    val dialog = AlertDialog.Builder(this@MainActivity).apply {
                        setView(view)
                    }.create()

                    with(view.main_dialog_download) {
                        text = when(GalleryDownloader.get(galleryBlock.id)) {
                            null -> getString(R.string.reader_fab_download)
                            else -> getString(R.string.reader_fab_download_cancel)
                        }
                        isEnabled = !(adapter as GalleryBlockAdapter).completeFlag.get(galleryBlock.id, false)
                        setOnClickListener {
                            val downloader = GalleryDownloader.get(galleryBlock.id)
                            if (downloader == null) {
                                GalleryDownloader(context, galleryBlock, true).start()
                                Histories.default.add(galleryBlock.id)
                            } else {
                                downloader.cancel()
                                downloader.clearNotification()
                            }

                            dialog.dismiss()
                        }
                    }

                    view.main_dialog_delete.setOnClickListener {
                        CoroutineScope(Dispatchers.Default).launch {
                            with(GalleryDownloader[galleryBlock.id]) {
                                this?.cancelAndJoin()
                                this?.clearNotification()
                            }
                            val cache = File(cacheDir, "imageCache/${galleryBlock.id}/images/")
                            cache.deleteRecursively()

                            dialog.dismiss()
                            (adapter as GalleryBlockAdapter).completeFlag.put(galleryBlock.id, false)
                        }
                    }

                    dialog.show()

                    true
                }
        }
    }

    private var suggestionJob : Job? = null
    private fun setupSearchBar() {
        val searchInputView = findViewById<SearchInputView>(R.id.search_bar_text)
        //Change upper case letters to lower case
        searchInputView.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                s ?: return

                if (s.any { it.isUpperCase() })
                    s.replace(0, s.length, s.toString().toLowerCase())
            }
        })

        with(main_searchview as FloatingSearchView) {
            setOnMenuItemClickListener {
                when(it.itemId) {
                    R.id.main_menu_settings -> startActivityForResult(Intent(this@MainActivity, SettingsActivity::class.java), SETTINGS)
                    R.id.main_menu_search -> setSearchFocused(true)
                }
            }

            setOnQueryChangeListener { _, query ->
                clearSuggestions()

                if (query.isEmpty() or query.endsWith(' '))
                    return@setOnQueryChangeListener

                val currentQuery = query.split(" ").last().replace('_', ' ')

                suggestionJob?.cancel()

                suggestionJob = CoroutineScope(Dispatchers.IO).launch {
                    val suggestions = getSuggestionsForQuery(currentQuery).map { TagSuggestion(it) }

                    withContext(Dispatchers.Main) {
                        swapSuggestions(suggestions)
                    }
                }
            }

            setOnBindSuggestionCallback { _, leftIcon, textView, item, _ ->
                val suggestion = item as TagSuggestion

                leftIcon.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        when(suggestion.n) {
                            "female" -> R.drawable.ic_gender_female
                            "male" -> R.drawable.ic_gender_male
                            "language" -> R.drawable.ic_translate
                            "group" -> R.drawable.ic_account_group
                            "character" -> R.drawable.ic_account_star
                            "series" -> R.drawable.ic_book_open
                            "artist" -> R.drawable.ic_brush
                            else -> R.drawable.ic_tag
                        },
                        null)
                )

                val text = "${suggestion.s}\n ${suggestion.t}"

                val len = text.length
                val left = suggestion.s.length

                textView.text = SpannableString(text).apply {
                    val s = AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE)
                    setSpan(s, left, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(SetLineOverlap(true), 1, len-2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(SetLineOverlap(false), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            setOnSearchListener(object : FloatingSearchView.OnSearchListener {
                override fun onSuggestionClicked(searchSuggestion: SearchSuggestion?) {
                    val suggestion = searchSuggestion as TagSuggestion

                    with(searchInputView.text) {
                        delete(if (lastIndexOf(' ') == -1) 0 else lastIndexOf(' ')+1, length)
                        append("${suggestion.n}:${suggestion.s.replace(Regex("\\s"), "_")} ")
                    }

                    clearSuggestions()
                }

                override fun onSearchAction(currentQuery: String?) {
                    //Do search on onFocusCleared()
                }
            })

            setOnFocusChangeListener(object: FloatingSearchView.OnFocusChangeListener {
                override fun onFocus() {
                    //Do Nothing
                }

                override fun onFocusCleared() {
                    suggestionJob?.cancel()

                    val query = searchInputView.text.toString()

                    if (query != this@MainActivity.query) {
                        this@MainActivity.query = query

                        CoroutineScope(Dispatchers.Main).launch {
                            cancelFetch()
                            clearGalleries()
                            fetchGalleries(query)
                            loadBlocks()
                        }
                    }
                }
            })

            attachNavigationDrawerToMenuButton(main_drawer_layout)
        }
    }

    private fun cancelFetch() {
        runBlocking {
            galleryIDs?.cancelAndJoin()
            loadingJob?.cancelAndJoin()
        }
    }

    private fun clearGalleries() {
        galleries.clear()

        with(main_recyclerview.adapter as GalleryBlockAdapter?) {
            this ?: return@with

            this.completeFlag.clear()
            this.notifyDataSetChanged()
        }

        main_noresult.visibility = View.INVISIBLE
        main_progressbar.show()
        main_swipe_layout.isRefreshing = false
    }

    private fun fetchGalleries(query: String, from: Int = 0) {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)
        val perPage = preference.getString("per_page", "25")?.toInt() ?: 25
        val defaultQuery = preference.getString("default_query", "")!!

        galleryIDs = null

        if (galleryIDs?.isActive == true)
            return

        galleryIDs = CoroutineScope(Dispatchers.IO).async {
            when {
                query.contains("HISTORY") ->
                    Histories.default.toList()
                query.isEmpty() and defaultQuery.isEmpty() ->
                    fetchNozomi(start = from, count = perPage)
                else ->
                    doSearch("$defaultQuery $query")
            }
        }
    }

    private fun loadBlocks() {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)
        val perPage = preference.getString("per_page", "25")?.toInt() ?: 25
        val defaultQuery = preference.getString("default_query", "")!!

        loadingJob = CoroutineScope(Dispatchers.IO).launch {
            val galleryIDs = galleryIDs?.await()

            if (galleryIDs.isNullOrEmpty()) { //No result
                withContext(Dispatchers.Main) {
                    main_noresult.visibility = View.VISIBLE
                    main_progressbar.hide()
                }

                return@launch
            }

            if (query.isEmpty() and defaultQuery.isEmpty())
                fetchGalleries("", galleries.size+perPage)
            else
                with(main_recyclerview.adapter as GalleryBlockAdapter) {
                    noMore = galleries.size + perPage >= galleryIDs.size
                }

            when {
                query.isEmpty() and defaultQuery.isEmpty() ->
                    galleryIDs
                else ->
                    galleryIDs.slice(galleries.size until Math.min(galleries.size+perPage, galleryIDs.size))
            }.chunked(5).let { chunks ->
                for (chunk in chunks)
                    chunk.map {
                        async {
                            try {
                                val json = Json(JsonConfiguration.Stable)
                                val serializer = GalleryBlock.serializer()

                                val galleryBlock =
                                    File(cacheDir, "imageCache/$it/galleryBlock.json").let { cache ->
                                        when {
                                            cache.exists() -> json.parse(serializer, cache.readText())
                                            else -> {
                                                getGalleryBlock(it).apply {
                                                    this ?: return@apply

                                                    if (!cache.parentFile.exists())
                                                        cache.parentFile.mkdirs()

                                                    cache.writeText(json.stringify(serializer, this))
                                                }
                                            }
                                        }
                                    } ?: return@async null

                                val thumbnail = async {
                                    val ext = galleryBlock.thumbnails[0].split('.').last()
                                    File(cacheDir, "imageCache/$it/thumbnail.$ext").apply {
                                        val cache = this

                                        if (!cache.exists())
                                            try {
                                                with(URL(galleryBlock.thumbnails[0]).openConnection() as HttpsURLConnection) {
                                                    if (!cache.parentFile.exists())
                                                        cache.parentFile.mkdirs()

                                                    inputStream.copyTo(FileOutputStream(cache))
                                                }
                                            } catch (e: Exception) {
                                                cache.delete()
                                            }
                                    }.absolutePath
                                }

                                Pair(galleryBlock, thumbnail)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.forEach {
                        val galleryBlock = it.await()

                        withContext(Dispatchers.Main) {
                            main_progressbar.hide()

                            if (galleryBlock != null) {
                                galleries.add(galleryBlock)

                                main_recyclerview.adapter?.notifyItemInserted(galleries.size - 1)
                            }
                        }
                    }
            }
        }
    }
}
