package xyz.quaver.pupil

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.text.*
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
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
import xyz.quaver.hitomi.*
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.types.TagSuggestion
import xyz.quaver.pupil.util.SetLineOverlap
import xyz.quaver.pupil.util.checkUpdate
import xyz.quaver.pupil.util.getApkUrl
import java.io.File
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 4585
    private val galleries = ArrayList<Pair<GalleryBlock, Bitmap?>>()

    private var isLoading = false
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_main)

        checkPermission()

        update()

        main_appbar_layout.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, p1 ->
                main_searchview.translationY = p1.toFloat()
                main_recyclerview.translationY = p1.toFloat()
            }
        )

        with(main_swipe_layout) {
            setProgressViewOffset(false, 0, resources.getDimensionPixelSize(R.dimen.progress_view_offset))

            setOnRefreshListener {
                runBlocking {
                    cleanJob?.join()
                }
                fetchGalleries(query, true)
            }
        }

        main_nav_view.setNavigationItemSelectedListener {
            Log.d("Pupil", it.itemId.toString())

            true
        }

        setupRecyclerView()
        setupSearchBar()
        fetchGalleries(query)
    }

    override fun onBackPressed() {
        if (main_drawer_layout.isDrawerOpen(GravityCompat.START))
            main_drawer_layout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
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
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun update() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            return

        CoroutineScope(Dispatchers.Default).launch {
            val update =
                checkUpdate(getString(R.string.release_url), BuildConfig.VERSION_NAME) ?: return@launch

            val (url, fileName) = getApkUrl(update, getString(R.string.release_name)) ?: return@launch

            val dialog = AlertDialog.Builder(this@MainActivity).apply {
                setTitle(R.string.update_title)
                setMessage(getString(R.string.update_message, update["tag_name"], BuildConfig.VERSION_NAME))
                setPositiveButton(android.R.string.yes) { _, _ ->
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
                }
            }
            addOnScrollListener(
                object: RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                        if (!isLoading)
                            if (layoutManager.findLastCompletelyVisibleItemPosition() == galleries.size)
                                fetchGalleries(query)
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

                        fetchGalleries(query, true)
                    }
                }
            })

            attachNavigationDrawerToMenuButton(main_drawer_layout)
        }
    }

    private val cache = ArrayList<Int>()
    private var currentFetchingJob: Job? = null
    private var cleanJob: Job? = null

    private fun cancelFetch() {
        isLoading = false

        runBlocking {
            cleanJob?.join()
            currentFetchingJob?.cancelAndJoin()
        }
    }

    private fun fetchGalleries(query: String, clear: Boolean = false) {
        val preference = PreferenceManager.getDefaultSharedPreferences(this)
        val perPage = preference.getString("per_page", "25")?.toInt() ?: 25
        val defaultQuery = preference.getString("default_query", "")!!

        if (clear) {
            cancelFetch()
            cleanJob = CoroutineScope(Dispatchers.Main).launch {
                cache.clear()
                galleries.clear()

                main_recyclerview.adapter?.notifyDataSetChanged()

                main_noresult.visibility = View.INVISIBLE
                main_progressbar.show()
                main_swipe_layout.isRefreshing = false
            }
        }

        if (isLoading)
            return

        isLoading = true

        currentFetchingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val galleryIDs: List<Int>

                cleanJob?.join()

                if (query.isEmpty() && defaultQuery.isEmpty())
                    galleryIDs = fetchNozomi(start = galleries.size, count = perPage)
                else {
                    if (cache.isEmpty())
                        cache.addAll(doSearch("$defaultQuery $query"))

                    galleryIDs = cache.slice(galleries.size until Math.min(galleries.size + perPage, cache.size))

                    with(main_recyclerview.adapter as GalleryBlockAdapter) {
                        noMore = galleries.size + perPage >= cache.size
                    }
                }

                if (query.isNotEmpty() and defaultQuery.isNotEmpty() and cache.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        main_noresult.visibility = View.VISIBLE
                        main_progressbar.hide()
                    }
                }

                galleryIDs.chunked(4).forEach { chunked ->
                    chunked.map {
                        async {
                            val galleryBlock = getGalleryBlock(it)
                            val thumbnail: Bitmap

                            with(galleryBlock.thumbnails[0].openConnection() as HttpsURLConnection) {
                                thumbnail = BitmapFactory.decodeStream(inputStream)
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
            } finally {
                isLoading = false
            }
        }
    }
}
