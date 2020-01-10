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

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_galleryblock.*
import kotlinx.android.synthetic.main.gallery_details.view.*
import kotlinx.android.synthetic.main.item_gallery_details.view.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.Gallery
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.getGallery
import xyz.quaver.hitomi.getGalleryBlock
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.GalleryBlockAdapter
import xyz.quaver.pupil.adapters.ThumbnailAdapter
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.ItemClickSupport
import xyz.quaver.pupil.util.wordCapitalize

class GalleryDialog(context: Context, private val galleryID: Int) : Dialog(context) {

    private val languages = context.resources.getStringArray(R.array.languages).map {
        it.split("|").let { split ->
            Pair(split[0], split[1])
        }
    }.toMap()

    private val glide = Glide.with(context)

    val onChipClickedHandler = ArrayList<((Tag) -> (Unit))>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_galleryblock)

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
                (context.applicationContext as Pupil).histories.add(galleryID)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gallery = getGallery(galleryID)

                launch(Dispatchers.Main) {
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

                    Glide.with(context)
                        .load(gallery.cover)
                        .apply {
                            if (BuildConfig.CENSOR)
                                override(5, 8)
                        }.into(gallery_cover)

                    addDetails(gallery)
                    addThumbnails(gallery)
                    addRelated(gallery)
                }
            } catch (e: Exception) {
                Snackbar.make(gallery_layout, R.string.unable_to_connect, Snackbar.LENGTH_INDEFINITE).show()
            }
        }
    }

    private fun addDetails(gallery: Gallery) {
        val inflater = LayoutInflater.from(context)
        
        inflater.inflate(R.layout.gallery_details, gallery_contents, false).apply {
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
                    gallery.tags.map {
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
                            Chip(context).apply {
                                chipIcon = when(tag.area) {
                                    "male" -> {
                                        setChipBackgroundColorResource(R.color.material_blue_700)
                                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                        ContextCompat.getDrawable(context, R.drawable.ic_gender_male_white)
                                    }
                                    "female" -> {
                                        setChipBackgroundColorResource(R.color.material_pink_600)
                                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                        ContextCompat.getDrawable(context, R.drawable.ic_gender_female_white)
                                    }
                                    else -> null
                                }

                                text = when (tag.area) {
                                    "language" -> languages[tag.tag]
                                    else -> tag.tag.wordCapitalize()
                                }

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

        inflater.inflate(R.layout.gallery_details, gallery_contents, false).apply {
            gallery_details.setText(R.string.gallery_thumbnails)

            RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, 3)
                adapter = ThumbnailAdapter(glide, gallery.thumbnails)
            }.let {
                gallery_details_contents.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }.let {
            gallery_contents.addView(it)
        }
    }

    private fun addRelated(gallery: Gallery) {
        val inflater = LayoutInflater.from(context)
        val galleries = ArrayList<Pair<GalleryBlock, Deferred<String>>>()

        val adapter = GalleryBlockAdapter(glide, galleries).apply {
            onChipClickedHandler.add { tag ->
                this@GalleryDialog.onChipClickedHandler.forEach { handler ->
                    handler.invoke(tag)
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            gallery.related.forEachIndexed { i, galleryID ->
                async(Dispatchers.IO) {
                    getGalleryBlock(galleryID)
                }.let {
                    val galleryBlock = it.await() ?: return@let

                    galleries.add(Pair(galleryBlock, GlobalScope.async { galleryBlock.thumbnails.first() }))
                    adapter.notifyItemInserted(i)
                }
            }
        }

        inflater.inflate(R.layout.gallery_details, gallery_contents, false).apply {
            gallery_details.setText(R.string.gallery_related)

            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter

                ItemClickSupport.addTo(this)
                    .setOnItemClickListener { _, position, _ ->
                        context.startActivity(Intent(context, ReaderActivity::class.java).apply {
                            putExtra("galleryID", galleries[position].first.id)
                        })
                        (context.applicationContext as Pupil).histories.add(galleries[position].first.id)
                    }
                    .setOnItemLongClickListener { _, position, _ ->
                        GalleryDialog(
                            context,
                            galleries[position].first.id
                        ).apply {
                            onChipClickedHandler.add { tag ->
                                this@GalleryDialog.onChipClickedHandler.forEach { it.invoke(tag) }
                            }
                        }.show()

                        true
                    }
            }.let {
                gallery_details_contents.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }.let {
            gallery_contents.addView(it)
        }
    }

}