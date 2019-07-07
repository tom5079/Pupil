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

package xyz.quaver.pupil.adapters

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import xyz.quaver.hitomi.GalleryBlock
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.Pupil
import xyz.quaver.pupil.R
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.Histories
import xyz.quaver.pupil.util.getCachedGallery
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

class GalleryBlockAdapter(private val galleries: List<Pair<GalleryBlock, Deferred<String>>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class ViewType {
        NEXT,
        GALLERY,
        PREV
    }

    private lateinit var favorites: Histories

    inner class GalleryViewHolder(val view: CardView) : RecyclerView.ViewHolder(view) {
        fun bind(holder: GalleryViewHolder, item: Pair<GalleryBlock, Deferred<String>>) {
            with(view) {
                val resources = context.resources
                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()

                val (galleryBlock: GalleryBlock, thumbnail: Deferred<String>) = item

                val artists = galleryBlock.artists
                val series = galleryBlock.series

                CoroutineScope(Dispatchers.Main).launch {
                    val cache = thumbnail.await()

                    Glide.with(holder.view)
                        .load(cache)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .error(R.drawable.image_broken_variant)
                        .into(galleryblock_thumbnail)
                }

                //Check cache
                val readerCache = { File(getCachedGallery(context, galleryBlock.id), "reader.json") }
                val imageCache = { File(getCachedGallery(context, galleryBlock.id), "images") }

                if (readerCache.invoke().exists()) {
                    val reader = Json(JsonConfiguration.Stable)
                        .parse(Reader.serializer(), readerCache.invoke().readText())

                    with(galleryblock_progressbar) {
                        max = reader.readerItems.size
                        progress = imageCache.invoke().list()?.size ?: 0

                        visibility = View.VISIBLE
                    }
                } else {
                    galleryblock_progressbar.visibility = View.GONE
                }

                if (refreshTasks[this@GalleryViewHolder] == null) {
                    val refresh = Timer(false).schedule(0, 1000) {
                        post {
                            with(view.galleryblock_progressbar) {
                                progress = imageCache.invoke().list()?.size ?: 0

                                if (!readerCache.invoke().exists()) {
                                    visibility = View.GONE
                                    max = 0
                                    progress = 0

                                    view.galleryblock_progress_complete.visibility = View.INVISIBLE
                                } else {
                                    if (visibility == View.GONE) {
                                        val reader = Json(JsonConfiguration.Stable)
                                            .parse(Reader.serializer(), readerCache.invoke().readText())
                                        max = reader.readerItems.size
                                        visibility = View.VISIBLE
                                    }

                                    if (progress == max) {
                                        if (completeFlag.get(galleryBlock.id, false)) {
                                            with(view.galleryblock_progress_complete) {
                                                setImageResource(R.drawable.ic_progressbar)
                                                visibility = View.VISIBLE
                                            }
                                        } else {
                                            with(view.galleryblock_progress_complete) {
                                                setImageDrawable(AnimatedVectorDrawableCompat.create(context, R.drawable.ic_progressbar_complete).apply {
                                                    this?.start()
                                                })
                                                visibility = View.VISIBLE
                                            }
                                            completeFlag.put(galleryBlock.id, true)
                                        }
                                    } else
                                        view.galleryblock_progress_complete.visibility = View.INVISIBLE

                                    null
                                }
                            }
                        }
                    }

                    refreshTasks[this@GalleryViewHolder] = refresh
                }

                galleryblock_title.text = galleryBlock.title
                with(galleryblock_artist) {
                    text = artists.joinToString(", ") { it.wordCapitalize() }
                    visibility = when {
                        artists.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                    setOnClickListener {
                        if (artists.size > 1) {
                            AlertDialog.Builder(context).apply {
                                setAdapter(ArrayAdapter(context, android.R.layout.select_dialog_item, artists)) { _, index ->
                                    for (callback in onChipClickedHandler)
                                        callback.invoke(Tag("artist", artists[index]))
                                }
                            }.show()
                        } else {
                            for(callback in onChipClickedHandler)
                                callback.invoke(Tag("artist", artists.first()))
                        }
                    }
                }
                with(galleryblock_series) {
                    text =
                        resources.getString(
                            R.string.galleryblock_series,
                            series.joinToString(", ") { it.wordCapitalize() })
                    visibility = when {
                        series.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                    setOnClickListener {
                        setOnClickListener {
                            if (series.size > 1) {
                                AlertDialog.Builder(context).apply {
                                    setAdapter(ArrayAdapter(context, android.R.layout.select_dialog_item, series)) { _, index ->
                                        for (callback in onChipClickedHandler)
                                            callback.invoke(Tag("series", series[index]))
                                    }
                                }.show()
                            } else {
                                for(callback in onChipClickedHandler)
                                    callback.invoke(Tag("series", series.first()))
                            }
                        }
                    }
                }
                with(galleryblock_type) {
                    text = resources.getString(R.string.galleryblock_type, galleryBlock.type).wordCapitalize()
                    setOnClickListener {
                        setOnClickListener {
                            for(callback in onChipClickedHandler)
                                callback.invoke(Tag("type", galleryBlock.type))
                        }
                    }
                }
                with(galleryblock_language) {
                    text =
                        resources.getString(R.string.galleryblock_language, languages[galleryBlock.language])
                    visibility = when {
                        galleryBlock.language.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                    setOnClickListener {
                        setOnClickListener {
                            for(callback in onChipClickedHandler)
                                callback.invoke(Tag("language", galleryBlock.language))
                        }
                    }
                }

                galleryblock_tag_group.removeAllViews()
                galleryBlock.relatedTags.forEach {
                    val tag = Tag.parse(it).let {  tag ->
                        when {
                            tag.area != null -> tag
                            else -> Tag("tag", it)
                        }
                    }

                    val chip = LayoutInflater.from(context)
                        .inflate(R.layout.tag_chip, this, false) as Chip

                    val icon = when(tag.area) {
                        "male" -> {
                            chip.setChipBackgroundColorResource(R.color.material_blue_700)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_male_white)
                        }
                        "female" -> {
                            chip.setChipBackgroundColorResource(R.color.material_pink_600)
                            chip.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                            ContextCompat.getDrawable(context, R.drawable.ic_gender_female_white)
                        }
                        else -> null
                    }

                    chip.chipIcon = icon
                    chip.text = tag.tag.wordCapitalize()
                    chip.setOnClickListener {
                        for (callback in onChipClickedHandler)
                            callback.invoke(tag)
                    }

                    galleryblock_tag_group.addView(chip)
                }

                galleryblock_id.text = galleryBlock.id.toString()

                if (!::favorites.isInitialized)
                    favorites = (context.applicationContext as Pupil).favorites

                with(galleryblock_favorite) {
                    setImageResource(if (favorites.contains(galleryBlock.id)) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
                    setOnClickListener {
                        when {
                            favorites.contains(galleryBlock.id) -> {
                                favorites.remove(galleryBlock.id)

                                setImageResource(R.drawable.ic_star_empty)
                            }
                            else -> {
                                favorites.add(galleryBlock.id)

                                setImageDrawable(AnimatedVectorDrawableCompat.create(context, R.drawable.avd_star).apply {
                                    this ?: return@apply

                                    registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
                                        override fun onAnimationEnd(drawable: Drawable?) {
                                            setImageResource(R.drawable.ic_star_filled)
                                        }
                                    })
                                    start()
                                })
                            }
                        }
                    }
                }
            }
        }
    }
    class NextViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)
    class PrevViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)

    class ViewHolderFactory {
        companion object {
            fun getLayoutID(type: Int): Int {
                return when(ViewType.values()[type]) {
                    ViewType.NEXT -> R.layout.item_next
                    ViewType.PREV -> R.layout.item_prev
                    ViewType.GALLERY -> R.layout.item_galleryblock
                }
            }
        }
    }

    private fun String.wordCapitalize() : String {
        val result = ArrayList<String>()

        for (word in this.split(" "))
            result.add(word.capitalize())

        return result.joinToString(" ")
    }

    private val refreshTasks = HashMap<GalleryViewHolder, TimerTask>()
    val completeFlag = SparseBooleanArray()

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()

    var showNext = false
    var showPrev = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        fun getViewHolder(type: Int, view: View): RecyclerView.ViewHolder {
            return when(ViewType.values()[type]) {
                ViewType.NEXT -> NextViewHolder(view as LinearLayout)
                ViewType.PREV -> PrevViewHolder(view as LinearLayout)
                ViewType.GALLERY -> GalleryViewHolder(view as CardView)
            }
        }

        return getViewHolder(
            viewType,
            LayoutInflater.from(parent.context).inflate(
                ViewHolderFactory.getLayoutID(viewType),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GalleryViewHolder)
            holder.bind(holder, galleries[position-(if (showPrev) 1 else 0)])
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        if (holder is GalleryViewHolder) {
            val task = refreshTasks[holder] ?: return

            task.cancel()
            refreshTasks.remove(holder)
        }
    }

    override fun getItemCount() =
        (if (galleries.isEmpty()) 0 else galleries.size)+
        (if (showNext) 1 else 0)+
        (if (showPrev) 1 else 0)

    override fun getItemViewType(position: Int): Int {
        return when {
            showPrev && position == 0 -> ViewType.PREV
            showNext && position == galleries.size+(if (showPrev) 1 else 0) -> ViewType.NEXT
            else -> ViewType.GALLERY
        }.ordinal
    }
}