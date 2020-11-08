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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_gallery.*
import kotlinx.android.synthetic.main.dialog_gallery_details.view.*
import kotlinx.android.synthetic.main.dialog_gallery_dotindicator.view.*
import kotlinx.android.synthetic.main.item_gallery_details.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.quaver.hitomi.Gallery
import xyz.quaver.hitomi.getGallery
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.adapters.ThumbnailPageAdapter
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.ui.view.TagChip
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.wordCapitalize
import java.util.*
import kotlin.collections.ArrayList

class GalleryDialog(context: Context, private val galleryID: Int) : AlertDialog(context) {

    val onChipClickedHandler = ArrayList<((Tag) -> (Unit))>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_gallery)

        window?.attributes.apply {
            this ?: return@apply

            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        with(gallery_fab) {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_right))
            setOnClickListener {
                context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                    putExtra("galleryID", galleryID)
                })
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gallery = getGallery(galleryID)

                gallery_cover.post {
                    gallery_progressbar.visibility = View.GONE
                    gallery_title.text = gallery.title
                    gallery_artist.text = gallery.artists.joinToString(", ") { it.wordCapitalize() }

                    with(gallery_type) {
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

                    gallery_cover.showImage(Uri.parse(gallery.cover))

                    addDetails(gallery)
                    addThumbnails(gallery)
                    addRelated(gallery)
                }
            } catch (e: Exception) {
                Snackbar.make(gallery_layout, R.string.unable_to_connect, Snackbar.LENGTH_INDEFINITE).apply {
                    if (Locale.getDefault().language == "ko")
                        setAction(context.getText(R.string.https_text)) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.https))))
                        }
                }.show()
            }
        }
    }

    private fun addDetails(gallery: Gallery) {
        val inflater = LayoutInflater.from(context)

        inflater.inflate(R.layout.dialog_gallery_details, gallery_contents, false).apply {
            gallery_details.setText(R.string.gallery_details)

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
                inflater.inflate(R.layout.item_gallery_details, gallery_details_contents, false).apply {
                    gallery_details_type.setText(title)

                    content.forEach { tag ->
                        gallery_details_tags.addView(
                            TagChip(context, tag).apply {
                                setOnClickListener {
                                    onChipClickedHandler.forEach { handler ->
                                        handler.invoke(tag)
                                    }
                                }
                            }
                        )
                    }
                }.let {
                    gallery_details_contents.addView(it)
                }
            }
        }.let {
            gallery_contents.addView(it)
        }
    }

    private fun addThumbnails(gallery: Gallery) {
        val inflater = LayoutInflater.from(context)

        inflater.inflate(R.layout.dialog_gallery_details, gallery_contents, false).apply {
            gallery_details.setText(R.string.gallery_thumbnails)

            val pager = ViewPager2(context).apply {
                adapter = ThumbnailPageAdapter(gallery.thumbnails)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            gallery_details_contents.addView(
                pager,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )

            LayoutInflater.from(context).inflate(R.layout.dialog_gallery_dotindicator, gallery_details_contents)

            gallery_dotindicator.setViewPager2(pager)
        }.let {
            gallery_contents.addView(it)
        }
    }

    private fun addRelated(gallery: Gallery) {
        val inflater = LayoutInflater.from(context)
        val galleries = ArrayList<Int>()

        val adapter = GalleryBlockAdapter(galleries).apply {
            onChipClickedHandler.add { tag ->
                this@GalleryDialog.onChipClickedHandler.forEach { handler ->
                    handler.invoke(tag)
                }
            }
        }

        inflater.inflate(R.layout.dialog_gallery_details, gallery_contents, false).apply {
            gallery_details.setText(R.string.gallery_related)

            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter

                ItemClickSupport.addTo(this).apply {
                    onItemClickListener = { _, position, _ ->
                        context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                            putExtra("galleryID", galleries[position])
                        })
                    }
                    onItemLongClickListener = { _, position, _ ->
                        GalleryDialog(context, galleries[position]).apply {
                            onChipClickedHandler.add { tag ->
                                this@GalleryDialog.onChipClickedHandler.forEach { it.invoke(tag) }
                            }
                        }.show()

                        true
                    }
                }
            }.let {
                gallery_details_contents.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }.let {
            gallery_contents.addView(it)
        }

        CoroutineScope(Dispatchers.IO).launch {
            gallery.related.forEach { galleryID ->
                Cache.getInstance(context, galleryID).getGalleryBlock()?.let {
                    galleries.add(galleryID)
                }
            }

            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
            }
        }
    }

}