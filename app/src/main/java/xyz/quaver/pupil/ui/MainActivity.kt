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
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.core.widget.ImageViewCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.pupil.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.MainActivityBinding
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialogFragment
import xyz.quaver.pupil.ui.dialog.SourceSelectDialog
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.*
import kotlin.math.*

class MainActivity :
    BaseActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    DIAware
{
    override val di by closestDI()

    private lateinit var binding: MainActivityBinding
    private val model: MainViewModel by viewModels()

    private var refreshOnResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)

        binding.contents.composeView.setContent {
            val searchResults: List<ItemInfo> by model.searchResults.observeAsState(emptyList())
            val source: Source? by model.source.observeAsState(null)
            val loading: Boolean by model.loading.observeAsState(false)

            val listState = rememberLazyListState()

            LaunchedEffect(listState) {
                var lastOffset = 0
                val querySectionHeight = binding.contents.searchview.binding.querySection.root.height.toFloat()

                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .collect { newOffset ->
                        val dy = newOffset - lastOffset
                        lastOffset = newOffset

                        binding.contents.searchview.apply {
                            translationY = (translationY - dy).coerceIn(-querySectionHeight .. 0f)
                        }
                    }
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(0.dp, 64.dp, 0.dp, 0.dp)) {
                    item(searchResults) {
                        searchResults.forEach { itemInfo ->
                            ProgressCardView(
                                progress = 0.5f,
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@MainActivity,
                                            ReaderActivity::class.java
                                        ).apply {
                                            putExtra("source", model.source.value!!.name)
                                            putExtra("id", itemInfo.itemID)
                                        })
                                }
                            ) {
                                source?.SearchResult(itemInfo = itemInfo)
                            }
                        }
                    }
                }

                if (loading)
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        setContentView(binding.root)

        if (Preferences["download_folder", ""].isEmpty())
            DownloadLocationDialogFragment().show(supportFragmentManager, "Download Location Dialog")

        checkUpdate(this)

        initView()

        model.query.observe(this)  {
            binding.contents.searchview.binding.querySection.searchBarText.run {
                if (text?.toString() != it) setText(it, TextView.BufferType.EDITABLE)
            }
        }

        model.availableSortMode.observe(this) {
            binding.contents.searchview.post {
                binding.contents.searchview.binding.querySection.menuView.menuItems.findMenu(R.id.sort)?.subMenu?.apply {
                    clear()

                    it.forEachIndexed { index, sortMode ->
                        add(R.id.sort_mode_group_id, index, Menu.NONE, sortMode).setOnMenuItemClickListener {
                            model.setPage(1)
                            model.sortModeIndex.value = it.itemId

                            children.forEachIndexed { menuIndex, menuItem ->
                                menuItem.isChecked = menuIndex == index
                            }

                            model.query()
                            true
                        }
                    }

                    setGroupCheckable(R.id.sort_mode_group_id, true, true)

                    children.first().isChecked = true
                }
            }
        }

        model.sourceIcon.observe(this) {
            binding.contents.searchview.post {
                (binding.contents.searchview.binding.querySection.menuView.getChildAt(1) as ImageView).apply {
                    ImageViewCompat.setImageTintList(this, null)

                    setImageResource(it)
                }
            }
        }

        model.suggestions.observe(this) { runOnUiThread {
            binding.contents.searchview.swapSuggestions(
                if (it.isEmpty()) listOf(NoResultSuggestion(getString(R.string.main_no_result))) else it
            )
        } }
    }

    override fun onResume() {
        super.onResume()
        if (refreshOnResume) {
            model.query()

            refreshOnResume = false
        }
    }

    override fun onBackPressed() {
        if (binding.drawer.isDrawerOpen(GravityCompat.START))
            binding.drawer.closeDrawer(GravityCompat.START)
        else if (!model.onBackPressed())
                super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (model.currentPage.value!! > 1) {
                    runOnUiThread {
                        model.prevPage()
                        model.query()
                    }
                }

                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (model.currentPage.value!! < model.totalPages.value!!) {
                    runOnUiThread {
                        model.nextPage()
                        model.query()
                    }
                }

                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun initView() {
        //NavigationView
        binding.navView.setNavigationItemSelectedListener(this)

        with (binding.contents.cancelFab) {
            setOnClickListener {

            }
        }

        with (binding.contents.jumpFab) {
            setOnClickListener {
                val perPage = Preferences["per_page", "25"].toInt()
                val editText = EditText(context)

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_jump_title)
                    setMessage(getString(
                        R.string.main_jump_message,
                        model.currentPage.value!!,
                        ceil(model.totalPages.value!! / perPage.toDouble()).roundToInt()
                    ))

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        model.setPage(editText.text.toString().toIntOrNull() ?: return@setPositiveButton)
                        model.query()
                    }
                }.show()
            }
        }

        with (binding.contents.randomFab) {
            setOnClickListener {
                setImageDrawable(CircularProgressDrawable(context))
/*
                model.random { runOnUiThread {
                    GalleryDialogFragment(model.source.value!!.name, it.itemID).apply {
                        onChipClickedHandler.add {
                            model.setQueryAndSearch(it.toQuery())
                            dismiss()
                        }
                    }.show(supportFragmentManager, "GalleryDialogFragment")
                } } */
            }
        }

        with (binding.contents.idFab) {
            setOnClickListener {
                val editText = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                }

                AlertDialog.Builder(context).apply {
                    setView(editText)
                    setTitle(R.string.main_open_gallery_by_id)

                    setPositiveButton(android.R.string.ok) { _, _ ->
                        val galleryID = editText.text.toString()
/*
                        GalleryDialogFragment(model.source.value!!.name, galleryID).apply {
                            onChipClickedHandler.add {
                                model.setQueryAndSearch(it.toQuery())
                                dismiss()
                            }
                        }.show(supportFragmentManager, "GalleryDialogFragment")*/
                    }
                }.show()
            }
        }

        setupSearchBar()
        // TODO: Save recent source
    }

    private fun setupSearchBar() {
        with (binding.contents.searchview) {
            onMenuItemClickListener = {
                onActionMenuItemSelected(it)
            }

            onQueryChangeListener = { _, query ->
                model.query.value = query

                model.suggestion()

                swapSuggestions(listOf(LoadingSuggestion(getText(R.string.reader_loading).toString())))
            }

            onSuggestionBinding = model.source.value!!::onSuggestionBind

            onFocusChangeListener = object: FloatingSearchView.OnFocusChangeListener {
                override fun onFocus() {

                }

                override fun onFocusCleared() {
                    model.setPage(1)
                    model.query()
                }
            }

            attachNavigationDrawerToMenuButton(this@MainActivity.binding.drawer)
        }
    }

    private fun onActionMenuItemSelected(item: MenuItem?) {
        when(item?.itemId) {
            R.id.main_menu_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            R.id.source -> SourceSelectDialog().apply {
                onSourceSelectedListener = {
                    model.setSourceAndReset(it)

                    dismiss()
                }

                onSourceSettingsSelectedListener = {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java).putExtra(SettingsActivity.SETTINGS_EXTRA, it))

                    refreshOnResume = true
                    dismiss()
                }
            }.show(supportFragmentManager, null)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        runOnUiThread {
            binding.drawer.closeDrawers()

            when(item.itemId) {
                R.id.main_drawer_home -> model.setModeAndReset(MainViewModel.MainMode.SEARCH)
                R.id.main_drawer_history -> model.setModeAndReset(MainViewModel.MainMode.HISTORY)
                R.id.main_drawer_downloads -> model.setModeAndReset(MainViewModel.MainMode.DOWNLOADS)
                R.id.main_drawer_help -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.help))))
                R.id.main_drawer_github -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github))))
                R.id.main_drawer_homepage -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.home_page))))
                R.id.main_drawer_email -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.email))))
                R.id.main_drawer_kakaotalk -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discord))))
            }
        }

        return true
    }
}
