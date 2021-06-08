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
import android.view.Menu
import android.view.MenuItem
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.orhanobut.logger.Logger
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import xyz.quaver.floatingsearchview.FloatingSearchView
import xyz.quaver.pupil.*
import xyz.quaver.pupil.adapters.SearchResultsAdapter
import xyz.quaver.pupil.databinding.MainActivityBinding
import xyz.quaver.pupil.types.*
import xyz.quaver.pupil.ui.dialog.DownloadLocationDialogFragment
import xyz.quaver.pupil.ui.dialog.GalleryDialogFragment
import xyz.quaver.pupil.ui.dialog.SourceSelectDialog
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.ui.view.SwipePageTurnView
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.*
import java.util.regex.Pattern
import kotlin.math.*

class MainActivity :
    BaseActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    DIAware
{
    override val di by closestDI()

    private lateinit var binding: MainActivityBinding
    private val model: MainViewModel by viewModels()

    private var refreshOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                restore(this, url,
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

        model.query.observe(this)  {
            binding.contents.searchview.binding.querySection.searchBarText.run {
                if (text?.toString() != it) setText(it, TextView.BufferType.EDITABLE)
            }
        }

        model.availableSortMode.observe(this) {
            binding.contents.searchview.post {
                binding.contents.searchview.binding.querySection.menuView.menuItems.findMenu(R.id.sort).subMenu.apply {
                    clear()

                    it.forEach {
                        add(R.id.sort_mode_group_id, it.ordinal, Menu.NONE, it.name)
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

        model.searchResults.observe(this) {
            binding.contents.recyclerview.post {
                if (model.loading) {
                    if (it.isEmpty()) {
                        binding.contents.noresult.hide()
                        binding.contents.progressbar.show()

                        (binding.contents.recyclerview.adapter as RecyclerSwipeAdapter).run {
                            mItemManger.closeAllItems()

                            notifyDataSetChanged()
                        }

                        ViewCompat.animate(binding.contents.searchview)
                            .setDuration(100)
                            .setInterpolator(DecelerateInterpolator())
                            .translationY(0F)
                    }
                } else {
                    binding.contents.progressbar.hide()
                    if (it.isEmpty()) {
                        binding.contents.recyclerview.adapter?.notifyDataSetChanged()
                        binding.contents.noresult.show()
                    } else {
                        binding.contents.recyclerview.adapter?.notifyItemInserted(it.size-1)
                    }
                }
            }
        }

        model.suggestions.observe(this) { runOnUiThread {
            Logger.d(it)
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

    override fun onDestroy() {
        super.onDestroy()
        binding.contents.recyclerview.adapter = null
    }

    @OptIn(ExperimentalStdlibApi::class)
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
        binding.contents.recyclerview.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // -height of the search view < translationY < 0
                binding.contents.searchview.translationY =
                    min(
                        max(
                        binding.contents.searchview.translationY - dy,
                        -binding.contents.searchview.binding.querySection.root.height.toFloat()
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

                model.random { runOnUiThread {
                    GalleryDialogFragment(model.source.value!!.name, it.id).apply {
                        onChipClickedHandler.add {
                            model.setQueryAndSearch(it.toQuery())
                            dismiss()
                        }
                    }.show(supportFragmentManager, "GalleryDialogFragment")
                } }
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

                        GalleryDialogFragment(model.source.value!!.name, galleryID).apply {
                            onChipClickedHandler.add {
                                model.setQueryAndSearch(it.toQuery())
                                dismiss()
                            }
                        }.show(supportFragmentManager, "GalleryDialogFragment")
                    }
                }.show()
            }
        }

        with (binding.contents.swipePageTurnView) {
            setOnPageTurnListener(object: SwipePageTurnView.OnPageTurnListener {
                override fun onPrev(page: Int) {
                    model.prevPage()

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    model.query()
                }

                override fun onNext(page: Int) {
                    model.nextPage()

                    // disable pageturn until the contents are loaded
                    setCurrentPage(1, false)

                    ViewCompat.animate(binding.contents.searchview)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .translationY(0F)

                    model.query()
                }
            })
        }

        setupSearchBar()
        setupRecyclerView()
        // TODO: Save recent source
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerView() {
        with (binding.contents.recyclerview) {
            adapter = SearchResultsAdapter(model.searchResults).apply {
                onChipClickedHandler = {
                    model.setQueryAndSearch(it.toQuery())
                }
                onDownloadClickedHandler = { source, itemID ->

                    closeAllItems()
                }

                onDeleteClickedHandler = { source, itemID ->

                    closeAllItems()
                }
            }
            ItemClickSupport.addTo(this).apply {
                onItemClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCardView)
                        return@listener

                    val intent = Intent(this@MainActivity, ReaderActivity::class.java).apply {
                        putExtra("source", model.source.value!!.name)
                        putExtra("id", model.searchResults.value!![position].id)
                    }

                    //TODO: Maybe sprinkling some transitions will be nice :D
                    startActivity(intent)
                }

                onItemLongClickListener = listener@{ _, position, v ->
                    if (v !is ProgressCardView)
                        return@listener false

                    val result = model.searchResults.value!!.getOrNull(position) ?: return@listener true

                    GalleryDialogFragment(model.source.value!!.name, result.id).apply {
                        onChipClickedHandler.add {
                            model.setQueryAndSearch(it.toQuery())
                            dismiss()
                        }
                    }.show(supportFragmentManager, "GalleryDialogFragment")

                    true
                }
            }
        }
    }

    private fun setupSearchBar() {
        with (binding.contents.searchview) {
            onMenuStatusChangeListener = object: FloatingSearchView.OnMenuStatusChangeListener {
                override fun onMenuOpened() {
                    (this@MainActivity.binding.contents.recyclerview.adapter as SearchResultsAdapter).closeAllItems()
                }

                override fun onMenuClosed() {
                    //Do Nothing
                }
            }

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
        if (item?.groupId == R.id.sort_mode_group_id) {
            model.setPage(1)
            model.sortMode.value = model.availableSortMode.value?.let { availableSortMode ->
                availableSortMode.getOrElse(item.itemId) { availableSortMode.first() }
            }

            model.query()
        }
        else
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
