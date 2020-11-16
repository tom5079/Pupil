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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.interfaces.SwipeAdapterInterface
import com.github.piasy.biv.loader.ImageLoader
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.android.synthetic.main.view_progress_card.view.*
import kotlinx.coroutines.*
import xyz.quaver.hitomi.getGallery
import xyz.quaver.hitomi.getReader
import xyz.quaver.io.util.getChild
import xyz.quaver.pupil.R
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.view.ProgressCard
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.wordCapitalize
import java.io.File

class GalleryBlockAdapter(private val galleries: List<Int>) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>(), SwipeAdapterInterface {

    enum class ViewType {
        NEXT,
        GALLERY,
        PREV
    }

    var updateAll = true
    var thin: Boolean = Preferences["thin"]

    inner class GalleryViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private var galleryID: Int = 0

        init {
            CoroutineScope(Dispatchers.Main).launch {
                while (updateAll) {
                    updateProgress(view.context)
                    delay(1000)
                }
            }
        }

        private fun updateProgress(context: Context) = CoroutineScope(Dispatchers.Main).launch {
            with(view.galleryblock_card) {
                val imageList = Cache.getInstance(context, galleryID).metadata.imageList

                if (imageList == null) {
                    max = 0
                    return@with
                }

                progress = imageList.count { it != null }
                max = imageList.size

                view.galleryblock_id.setOnClickListener {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                        ClipData.newPlainText("gallery_id", galleryID.toString())
                    )
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }

                type = if (!imageList.contains(null)) {
                    val downloadManager = DownloadManager.getInstance(context)

                    if (downloadManager.getDownloadFolder(galleryID) == null)
                        ProgressCard.Type.CACHE
                    else
                        ProgressCard.Type.DOWNLOAD
                } else
                    ProgressCard.Type.LOADING
            }
        }

        fun bind(galleryID: Int) {
            this.galleryID = galleryID
            updateProgress(view.context)

            val cache = Cache.getInstance(view.context, galleryID)

            val galleryBlock = runBlocking {
                cache.getGalleryBlock()
            } ?: return

            with(view) {
                val resources = context.resources
                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()

                val artists = galleryBlock.artists
                val series = galleryBlock.series

                galleryblock_thumbnail.apply {
                    setOnClickListener {
                        view.performClick()
                    }
                    setOnLongClickListener {
                        view.performLongClick()
                    }
                    setFailureImage(ContextCompat.getDrawable(context, R.drawable.image_broken_variant))
                    setImageLoaderCallback(object: ImageLoader.Callback {
                        override fun onFail(error: Exception?) {
                            Cache.getInstance(context, galleryID).let { cache ->
                                cache.cacheFolder.getChild(".thumbnail").let { if (it.exists()) it.delete() }
                                cache.downloadFolder?.getChild(".thumbnail")?.let { if (it.exists()) it.delete() }
                            }
                        }

                        override fun onCacheHit(imageType: Int, image: File?) {}
                        override fun onCacheMiss(imageType: Int, image: File?) {}
                        override fun onFinish() {}
                        override fun onProgress(progress: Int) {}
                        override fun onStart() {}
                        override fun onSuccess(image: File?) {}
                    })
                    ssiv?.recycle()
                    CoroutineScope(Dispatchers.IO).launch {
                        cache.getThumbnail().let { launch(Dispatchers.Main) {
                            showImage(it)
                        } }
                    }
                }

                galleryblock_title.text = galleryBlock.title
                with(galleryblock_artist) {
                    text = artists.joinToString { it.wordCapitalize() }
                    visibility = when {
                        artists.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        val gallery = runCatching {
                            getGallery(galleryID)
                        }.getOrNull()

                        if (gallery?.groups?.isNotEmpty() != true)
                            return@launch

                        launch(Dispatchers.Main) {
                            text = context.getString(
                                R.string.galleryblock_artist_with_group,
                                artists.joinToString { it.wordCapitalize() },
                                gallery.groups.joinToString { it.wordCapitalize() }
                            )
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
                }
                galleryblock_type.text = resources.getString(R.string.galleryblock_type, galleryBlock.type).wordCapitalize()
                with(galleryblock_language) {
                    text =
                        resources.getString(R.string.galleryblock_language, languages[galleryBlock.language])
                    visibility = when {
                        galleryBlock.language.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }

                with(galleryblock_tag_group) {
                    onClickListener = {
                        onChipClickedHandler.forEach { callback ->
                            callback.invoke(it)
                        }
                    }

                    tags.clear()

                    CoroutineScope(Dispatchers.IO).launch {
                        tags.addAll(
                            galleryBlock.relatedTags.sortedBy {
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
                                Tag.parse(it)
                            }
                        )

                        launch(Dispatchers.Main) {
                            refresh()
                        }
                    }
                }

                galleryblock_id.text = galleryBlock.id.toString()
                galleryblock_pagecount.text = "-"
                CoroutineScope(Dispatchers.IO).launch {
                    val pageCount = kotlin.runCatching {
                        getReader(galleryBlock.id).galleryInfo.files.size
                    }.getOrNull() ?: return@launch
                    withContext(Dispatchers.Main) {
                        galleryblock_pagecount.text = context.getString(R.string.galleryblock_pagecount, pageCount)
                    }
                }

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


                // Make some views invisible to make it thinner
                if (thin) {
                    galleryblock_tag_group.visibility = View.GONE
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

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()
    var onDownloadClickedHandler: ((Int) -> Unit)? = null
    var onDeleteClickedHandler: ((Int) -> Unit)? = null

    var showNext = false
    var showPrev = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        fun getViewHolder(type: Int, view: View): RecyclerView.ViewHolder {
            return when(ViewType.values()[type]) {
                ViewType.NEXT -> NextViewHolder(view as LinearLayout)
                ViewType.PREV -> PrevViewHolder(view as LinearLayout)
                ViewType.GALLERY -> GalleryViewHolder(view as ProgressCard)
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
        if (holder is GalleryViewHolder) {
            val galleryID = galleries[position-(if (showPrev) 1 else 0)]

            holder.bind(galleryID)

            holder.view.galleryblock_card.download.setOnClickListener {
                onDownloadClickedHandler?.invoke(position)
            }

            holder.view.galleryblock_card.delete.setOnClickListener {
                onDeleteClickedHandler?.invoke(position)
            }

            mItemManger.bindView(holder.view, position)

            holder.view.galleryblock_card.swipe_layout.addSwipeListener(object: SwipeLayout.SwipeListener {
                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)

                    holder.view.galleryblock_card.download.text =
                        if (DownloadManager.getInstance(holder.view.context).isDownloading(galleryID))
                            holder.view.context.getString(android.R.string.cancel)
                        else
                            holder.view.context.getString(R.string.main_download)
                }

                override fun onClose(layout: SwipeLayout?) {}
                override fun onHandRelease(layout: SwipeLayout?, xvel: Float, yvel: Float) {}
                override fun onOpen(layout: SwipeLayout?) {}
                override fun onStartClose(layout: SwipeLayout?) {}
                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {}
            })
        }
    }

    override fun getItemCount() =
        galleries.size +
        (if (showNext) 1 else 0) +
        (if (showPrev) 1 else 0)

    override fun getItemViewType(position: Int): Int {
        return when {
            showPrev && position == 0 -> ViewType.PREV
            showNext && position == galleries.size+(if (showPrev) 1 else 0) -> ViewType.NEXT
            else -> ViewType.GALLERY
        }.ordinal
    }

    override fun getSwipeLayoutResourceId(position: Int) = R.id.swipe_layout
}