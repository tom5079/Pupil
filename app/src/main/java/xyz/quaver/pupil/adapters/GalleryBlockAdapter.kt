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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.interfaces.SwipeAdapterInterface
import com.github.piasy.biv.loader.ImageLoader
import kotlinx.coroutines.*
import xyz.quaver.io.util.getChild
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.GalleryblockItemBinding
import xyz.quaver.pupil.favoriteTags
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.hitomi.getGallery
import xyz.quaver.pupil.hitomi.getGalleryInfo
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.view.ProgressCard
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.wordCapitalize
import java.io.File

class GalleryBlockAdapter(private val galleries: List<Int>) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>(), SwipeAdapterInterface {

    var updateAll = true
    var thin: Boolean = Preferences["thin"]

    inner class GalleryViewHolder(val binding: GalleryblockItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var galleryID: Int = 0

        init {
            CoroutineScope(Dispatchers.Main).launch {
                while (updateAll) {
                    updateProgress(itemView.context)
                    delay(1000)
                }
            }
        }

        private fun updateProgress(context: Context) = CoroutineScope(Dispatchers.Main).launch {
            with(binding.galleryblockCard) {
                val imageList = Cache.getInstance(context, galleryID).metadata.imageList

                if (imageList == null) {
                    max = 0
                    return@with
                }

                progress = imageList.count { it != null }
                max = imageList.size

                this@GalleryViewHolder.binding.galleryblockId.setOnClickListener {
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
            updateProgress(itemView.context)

            val cache = Cache.getInstance(itemView.context, galleryID)

            CoroutineScope(Dispatchers.IO).launch {
                val galleryBlock = cache.getGalleryBlock() ?: return@launch

                launch(Dispatchers.Main) {
                    val resources = itemView.context.resources
                    val languages = resources.getStringArray(R.array.languages).map {
                        it.split("|").let { split ->
                            Pair(split[0], split[1])
                        }
                    }.toMap()

                    val artists = galleryBlock.artists
                    val series = galleryBlock.series

                    binding.galleryblockThumbnail.apply {
                        setOnClickListener {
                            itemView.performClick()
                        }
                        setOnLongClickListener {
                            itemView.performLongClick()
                        }
                        setFailureImage(ContextCompat.getDrawable(context, R.drawable.image_broken_variant))
                        setImageLoaderCallback(object: ImageLoader.Callback {
                            override fun onFail(error: Exception?) {
                                Cache.delete(context, galleryID)
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

                    binding.galleryblockTitle.text = galleryBlock.title
                    with(binding.galleryblockArtist) {
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
                    with(binding.galleryblockSeries) {
                        text =
                            resources.getString(
                                R.string.galleryblock_series,
                                series.joinToString(", ") { it.wordCapitalize() })
                        visibility = when {
                            series.isNotEmpty() -> View.VISIBLE
                            else -> View.GONE
                        }
                    }
                    binding.galleryblockType.text = resources.getString(R.string.galleryblock_type, galleryBlock.type).wordCapitalize()
                    with(binding.galleryblockLanguage) {
                        text =
                            resources.getString(R.string.galleryblock_language, languages[galleryBlock.language])
                        visibility = when {
                            !galleryBlock.language.isNullOrEmpty() -> View.VISIBLE
                            else -> View.GONE
                        }
                    }

                    with(binding.galleryblockTagGroup) {
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

                    binding.galleryblockId.text = galleryBlock.id.toString()
                    binding.galleryblockPagecount.text = "-"
                    CoroutineScope(Dispatchers.IO).launch {
                        val pageCount = kotlin.runCatching {
                            getGalleryInfo(galleryBlock.id).files.size
                        }.getOrNull() ?: return@launch
                        withContext(Dispatchers.Main) {
                            binding.galleryblockPagecount.text = itemView.context.getString(R.string.galleryblock_pagecount, pageCount)
                        }
                    }

                    with(binding.galleryblockFavorite) {
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

            // Make some views invisible to make it thinner
            if (thin) {
                binding.galleryblockTagGroup.visibility = View.GONE
            }
        }
    }

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()
    var onDownloadClickedHandler: ((Int) -> Unit)? = null
    var onDeleteClickedHandler: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return GalleryViewHolder(GalleryblockItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GalleryViewHolder) {
            val galleryID = galleries[position]

            holder.bind(galleryID)

            holder.binding.galleryblockCard.binding.download.setOnClickListener {
                onDownloadClickedHandler?.invoke(position)
            }

            holder.binding.galleryblockCard.binding.delete.setOnClickListener {
                onDeleteClickedHandler?.invoke(position)
            }

            mItemManger.bindView(holder.binding.root, position)

            holder.binding.galleryblockCard.binding.swipeLayout.addSwipeListener(object: SwipeLayout.SwipeListener {
                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)

                    holder.binding.galleryblockCard.binding.download.text =
                        if (DownloadManager.getInstance(holder.binding.root.context).isDownloading(galleryID))
                            holder.binding.root.context.getString(android.R.string.cancel)
                        else
                            holder.binding.root.context.getString(R.string.main_download)
                }

                override fun onClose(layout: SwipeLayout?) {}
                override fun onHandRelease(layout: SwipeLayout?, xvel: Float, yvel: Float) {}
                override fun onOpen(layout: SwipeLayout?) {}
                override fun onStartClose(layout: SwipeLayout?) {}
                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {}
            })
        }
    }

    override fun getItemCount() = galleries.size

    override fun getSwipeLayoutResourceId(position: Int) = R.id.swipe_layout
}