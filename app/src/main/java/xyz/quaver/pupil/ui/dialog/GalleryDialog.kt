/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

package xyz.quaver.pupil.ui.dialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.quaver.hitomi.Gallery
import xyz.quaver.hitomi.getGallery
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.SearchResultsAdapter
import xyz.quaver.pupil.adapters.ThumbnailPageAdapter
import xyz.quaver.pupil.databinding.*
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.sources.hitomi.Hitomi
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.ui.view.TagChip
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.wordCapitalize
import java.util.*
import kotlin.collections.ArrayList

class GalleryDialog(context: Context, private val galleryID: String) : AlertDialog(context) {

    val onChipClickedHandler = ArrayList<((Tag) -> (Unit))>()

    private lateinit var binding: GalleryDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GalleryDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.attributes.apply {
            this ?: return@apply

            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        with(binding.fab) {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_right))
            setOnClickListener {
                context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                    putExtra("galleryID", galleryID)
                })
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gallery = getGallery(galleryID.toInt())

                launch (Dispatchers.Main) {
                    binding.progressbar.visibility = View.GONE
                    binding.title.text = gallery.title
                    binding.artist.text = gallery.artists.joinToString(", ") { it.wordCapitalize() }

                    with(binding.type) {
                        text = gallery.type.wordCapitalize()
                        setOnClickListener {
                            gallery.type.let {
                                when (it) {
                                    "artist CG" -> "artistcg"
                                    "game CG" -> "gamecg"
                                    else -> it
                                }
                            }.let {
                                onChipClickedHandler.forEach { handler ->
                                    handler.invoke(Tag("type", it))
                                }
                            }
                        }
                    }

                    binding.cover.showImage(Uri.parse(gallery.cover))

                    addDetails(gallery)
                    addThumbnails(gallery)
                    addRelated(gallery)
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, R.string.unable_to_connect, Snackbar.LENGTH_INDEFINITE).apply {
                    if (Locale.getDefault().language == "ko")
                        setAction(context.getText(R.string.https_text)) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.https))))
                        }
                }.show()
            }
        }
    }

    private fun addDetails(gallery: Gallery) {
        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_details)

            listOf(
                R.string.gallery_artists,
                R.string.gallery_groups,
                R.string.gallery_language,
                R.string.gallery_series,
                R.string.gallery_characters,
                R.string.gallery_tags
            ).zip(
                listOf(
                    gallery.artists.map { Tag("artist", it) },
                    gallery.groups.map { Tag("group", it) },
                    listOf(gallery.language).map { Tag("language", it) },
                    gallery.series.map { Tag("series", it) },
                    gallery.characters.map { Tag("character", it) },
                    gallery.tags.sortedBy {
                        val tag = Tag.parse(it)

                        if (favoriteTags.contains(tag))
                            -1
                        else
                            when(Tag.parse(it).area) {
                                "female" -> 0
                                "male" -> 1
                                else -> 2
                            }
                    }.map {
                        Tag.parse(it).let { tag ->
                            when {
                                tag.area != null -> tag
                                else -> Tag("tag", it)
                            }
                        }
                    }
                )
            ).filter {
                    (_, content) -> content.isNotEmpty()
            }.forEach { (title, content) ->
                GalleryDialogTagsBinding.inflate(layoutInflater, contents, true).apply {
                    type.setText(title)

                    content.forEach { tag ->
                        tags.addView(
                            TagChip(context, tag).apply {
                                setOnClickListener {
                                    onChipClickedHandler.forEach { handler ->
                                        handler.invoke(tag)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun addThumbnails(gallery: Gallery) {
        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_thumbnails)

            val pager = ViewPager2(context).apply {
                adapter = ThumbnailPageAdapter(gallery.thumbnails)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            contents.addView(
                pager,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )

            // TODO: Change to direct allocation
            GalleryDialogDotindicatorBinding.inflate(layoutInflater, contents, true).apply {
                dotindicator.setViewPager2(pager)
            }
        }
    }

    private fun addRelated(gallery: Gallery) {
        val galleries = mutableListOf<SearchResult>()

        val adapter = SearchResultsAdapter(galleries).apply {
            onChipClickedHandler.add { tag ->
                this@GalleryDialog.onChipClickedHandler.forEach { handler ->
                    handler.invoke(tag)
                }
            }
        }

        GalleryDialogDetailsBinding.inflate(layoutInflater, binding.contents, true).apply {
            type.setText(R.string.gallery_related)

            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter

                ItemClickSupport.addTo(this).apply {
                    onItemClickListener = { _, position, _ ->
                        context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                            putExtra("galleryID", galleries[position].id)
                        })
                    }
                    onItemLongClickListener = { _, position, _ ->
                        GalleryDialog(context, galleries[position].id).apply {
                            onChipClickedHandler.add { tag ->
                                this@GalleryDialog.onChipClickedHandler.forEach { it.invoke(tag) }
                            }
                        }.show()

                        true
                    }
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                gallery.related.forEach { galleryID ->
                    Cache.getInstance(context, galleryID.toString()).getGalleryBlock()?.let {
                        galleries.add(
                            Hitomi.SearchResult(
                                it.id.toString(),
                                it.title,
                                it.thumbnails.first(),
                                it.artists
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

}