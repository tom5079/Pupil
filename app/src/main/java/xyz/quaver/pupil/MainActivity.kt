package xyz.quaver.pupil

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.text.*
import android.text.style.AlignmentSpan
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.content
import ru.noties.markwon.Markwon
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.types.TagSuggestion
import xyz.quaver.pupil.util.Histories
import xyz.quaver.pupil.util.SetLineOverlap
import xyz.quaver.pupil.util.checkUpdate
import xyz.quaver.pupil.util.getApkUrl
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 4585
    private val galleries = ArrayList<Pair<GalleryBlock, Deferred<String>>>()

    private var query = ""

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

        checkPermission()

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

                cancelFetch()
                clearGalleries()
                when(it.itemId) {
                    R.id.main_drawer_home -> {
                        query = query.replace("HISTORY", "")
                        fetchGalleries(query)
                    }
                    R.id.main_drawer_history -> {
                        query += "HISTORY"
                        fetchGalleries(query)
                    }
                }
                loadBlocks()
            }

            true
        }

        setupRecyclerView()
        setupSearchBar()
        fetchGalleries(query)
        loadBlocks()
    }

    override fun onBackPressed() {
        if (main_drawer_layout.isDrawerOpen(GravityCompat.START))
            main_drawer_layout.closeDrawer(GravityCompat.START)
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

    private fun checkPermission() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            if (permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) })
                AlertDialog.Builder(this).apply {
                    setTitle(R.string.warning)
                    setMessage(R.string.permission_explain)
                    setPositiveButton(android.R.string.ok) { _, _ -> }
                }.show()
            else
                ActivityCompat.requestPermissions(this, permissions, permissionRequestCode)
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

            for(line in markdown.split('\n')) {
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            return

        CoroutineScope(Dispatchers.Default).launch {
            val update =
                checkUpdate(getString(R.string.release_url), BuildConfig.VERSION_NAME) ?: return@launch

            val (url, fileName) = getApkUrl(update, getString(R.string.release_name)) ?: return@launch

            val dialog = AlertDialog.Builder(this@MainActivity).apply {
                setTitle(R.string.update_title)
                val msg = extractReleaseNote(update, Locale.getDefault().language)
                setMessage(Markwon.create(context).toMarkdown(msg))
                setPositiveButton(android.R.string.yes) { _, _ ->
                    Toast.makeText(
                        context, getString(R.string.update_download_started), Toast.LENGTH_SHORT
                    ).show()

                    val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    val desturi =
                        FileProvider.getUriForFile(
                            applicationContext,
                            "xyz.quaver.pupil.provider",
                            dest
                        )

                    if (dest.exists())
                        dest.delete()

                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setDescription(getString(R.string.update_notification_description))
                        setTitle(getString(R.string.app_name))
                        setDestinationUri(Uri.fromFile(dest))
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    }

                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val id = manager.enqueue(request)

                    registerReceiver(object: BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            val install = Intent(Intent.ACTION_VIEW).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                setDataAndType(desturi, manager.getMimeTypeForDownloadedFile(id))
                            }

                            startActivity(install)
                            unregisterReceiver(this)
                            finish()
                        }
                    }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
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
                setClickListener { galleryID, title ->
                    val intent = Intent(this@MainActivity, GalleryActivity::class.java)
                    intent.putExtra("GALLERY_ID", galleryID)
                    intent.putExtra("GALLERY_TITLE", title)

                    //TODO: Maybe sprinke some transitions will be nice :D
                    startActivity(intent)

                    Histories.default.add(galleryID)
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
                    R.id.main_menu_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
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

                        cancelFetch()
                        clearGalleries()
                        fetchGalleries(query)
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

        main_recyclerview.adapter?.notifyDataSetChanged()

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
            }.chunked(4).forEach { chunked ->
                chunked.map {
                    async {
                        val galleryBlock = getGalleryBlock(it)

                        val thumbnail = async {
                            val cache = File(cacheDir, "imageCache/$it/thumbnail.${galleryBlock.thumbnails[0].path.split('.').last()}")

                            if (!cache.exists())
                                with(galleryBlock.thumbnails[0].openConnection() as HttpsURLConnection) {
                                    if (!cache.parentFile.exists())
                                        cache.parentFile.mkdirs()

                                    inputStream.copyTo(FileOutputStream(cache))
                                }

                            cache.absolutePath
                        }

                        Pair(galleryBlock, thumbnail)
                    }
                }.forEach {
                    val galleryBlock = it.await()

                    withContext(Dispatchers.Main) {
                        main_progressbar.hide()

                        galleries.add(galleryBlock)
                        main_recyclerview.adapter?.notifyItemInserted(galleries.size - 1)
                    }
                }
            }
        }
    }
}
