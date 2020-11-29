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
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.interfaces.SwipeAdapterInterface
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.imagepipeline.image.ImageInfo
import kotlinx.coroutines.*
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.SearchResultItemBinding
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import kotlin.time.ExperimentalTime

class SearchResultsAdapter(private val results: List<SearchResult>) : RecyclerSwipeAdapter<SearchResultsAdapter.ViewHolder>(), SwipeAdapterInterface {

    var onChipClickedHandler: ((Tag) -> Unit)? = null
    var onDownloadClickedHandler: ((String) -> Unit)? = null
    var onDeleteClickedHandler: ((String) -> Unit)? = null

    // TODO: migrate to viewBinding
    val progressUpdateScope = CoroutineScope(Dispatchers.Main + Job())

    inner class ViewHolder(private val binding: SearchResultItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var itemID: String = ""

        private var bindJob: Job? = null

        init {
            progressUpdateScope.launch {
                while (true) {
                    updateProgress()
                    delay(1000)
                }
            }

            binding.root.binding.download.setOnClickListener {
                onDownloadClickedHandler?.invoke(itemID)
            }

            binding.root.binding.delete.setOnClickListener {
                onDeleteClickedHandler?.invoke(itemID)
            }

            binding.idView.setOnClickListener {
                (itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                    ClipData.newPlainText("item_id", itemID)
                )
                Toast.makeText(itemView.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }

            binding.root.binding.swipeLayout.addSwipeListener(object: SwipeLayout.SwipeListener {
                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)

                    binding.root.binding.download.text =
                        if (DownloadManager.getInstance(itemView.context).isDownloading(itemID))
                            itemView.context.getString(android.R.string.cancel)
                        else
                            itemView.context.getString(R.string.main_download)
                }

                override fun onOpen(layout: SwipeLayout?) {}
                override fun onStartClose(layout: SwipeLayout?) {}
                override fun onClose(layout: SwipeLayout?) {}
                override fun onHandRelease(layout: SwipeLayout?, xvel: Float, yvel: Float) {}
                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {}
            })

            binding.tagGroup.onClickListener = onChipClickedHandler
        }

        private fun updateProgress() = CoroutineScope(Dispatchers.Main).launch {
            with (itemView as ProgressCardView) {
                val imageList = Cache.getInstance(context, itemID).metadata.imageList

                if (imageList == null) {
                    max = 0
                    return@with
                }

                progress = imageList.count { it != null }
                max = imageList.size

                type = if (!imageList.contains(null)) {
                    val downloadManager = DownloadManager.getInstance(context)

                    if (downloadManager.getDownloadFolder(itemID) == null)
                        ProgressCardView.Type.CACHE
                    else
                        ProgressCardView.Type.DOWNLOAD
                } else
                    ProgressCardView.Type.LOADING
            }
        }

        val controllerListener = object: BaseControllerListener<ImageInfo>() {
            override fun onIntermediateImageSet(id: String?, imageInfo: ImageInfo?) {
                imageInfo?.let {
                    binding.thumbnail.aspectRatio = it.width / it.height.toFloat()
                }
            }

            override fun onFinalImageSet(id: String?, imageInfo: ImageInfo?, animatable: Animatable?) {
                imageInfo?.let {
                    binding.thumbnail.aspectRatio = it.width / it.height.toFloat()
                }
            }
        }
        fun bind(result: SearchResult) {
            bindJob?.cancel()
            itemID = result.id

            binding.thumbnail.controller = Fresco.newDraweeControllerBuilder()
                .setUri(result.thumbnail)
                .setOldController(binding.thumbnail.controller)
                .setControllerListener(controllerListener)
                .build()

            updateProgress()

            binding.title.text = result.title
            binding.idView.text = result.id

            binding.artist.visibility = if (result.artists.isEmpty()) View.GONE else View.VISIBLE
            binding.artist.text = result.artists

            with (binding.tagGroup) {
                tags.clear()
                tags.addAll(result.tags.map {
                    Tag.parse(it)
                })
                refresh()
            }

            binding.pagecount.text = "-"

            bindJob = MainScope().launch {
                val extra = result.extra.mapValues {
                    async(Dispatchers.IO) {
                        kotlin.runCatching { withTimeout(1000) {
                            it.value.invoke()
                        } }.getOrNull()
                    }
                }

                launch {
                    val extraType = listOf(
                        SearchResult.ExtraType.SERIES,
                        SearchResult.ExtraType.TYPE,
                        SearchResult.ExtraType.LANGUAGE
                    )

                    binding.extra.text = extra.entries.filter { it.key in extraType }.fold(StringBuilder()) { res, entry ->
                        entry.value.await().let {
                            if (!it.isNullOrEmpty()) {
                                res.append(
                                    itemView.context.getString(
                                        SearchResult.extraTypeMap[entry.key] ?: error(""),
                                        it
                                    )
                                )
                                res.append('\n')
                            }
                            res
                        }
                    }
                }

                launch {
                    extra[SearchResult.ExtraType.PAGECOUNT]?.await()?.let {
                        binding.pagecount.text =
                            itemView.context.getString(
                                SearchResult.extraTypeMap[SearchResult.ExtraType.PAGECOUNT] ?: error(""),
                                it
                            )
                    }
                }

                launch {
                    extra[SearchResult.ExtraType.GROUP]?.await().let {
                        if (!it.isNullOrEmpty())
                            binding.artist.text = itemView.context.getString(
                                R.string.galleryblock_artist_with_group,
                                result.artists,
                                it
                            )
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(SearchResultItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    @ExperimentalTime
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        mItemManger.bindView(holder.itemView, position)
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.swipe_layout

}