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

import android.annotation.SuppressLint
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
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.downloader.Downloader
import kotlin.time.ExperimentalTime

class SearchResultsAdapter(private val results: List<ItemInfo>) : RecyclerSwipeAdapter<SearchResultsAdapter.ViewHolder>(), SwipeAdapterInterface {

    var onChipClickedHandler: ((Tag) -> Unit)? = null
    var onDownloadClickedHandler: ((source: String, itemID: String) -> Unit)? = null
    var onDeleteClickedHandler: ((source: String, itemID: String) -> Unit)? = null

    // TODO: migrate to viewBinding
    val progressUpdateScope = CoroutineScope(Dispatchers.Main + Job())

    inner class ViewHolder(private val binding: SearchResultItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var source: String = ""
        var itemID: String = ""

        init {
            binding.root.binding.download.setOnClickListener {
                onDownloadClickedHandler?.invoke(source, itemID)
            }

            binding.root.binding.delete.setOnClickListener {
                onDeleteClickedHandler?.invoke(source, itemID)
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
                        if (Downloader.getInstance(itemView.context).isDownloading(source, itemID))
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

            CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    updateProgress()
                    delay(1000)
                }
            }
        }

        private val controllerListener = object: BaseControllerListener<ImageInfo>() {
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

        private fun updateProgress() {
            val cache = Cache.getInstance(itemView.context, source, itemID)

            binding.root.max = cache.metadata.imageList?.size ?: 0
            binding.root.progress = cache.metadata.imageList?.count { it != null } ?: 0

            binding.root.type = if (cache.metadata.imageList?.all { it != null } == true) { // Download completed
                if (DownloadManager.getInstance(itemView.context).getDownloadFolder(source, itemID) != null)
                    ProgressCardView.Type.DOWNLOAD
                else
                    ProgressCardView.Type.CACHE
            } else
                ProgressCardView.Type.LOADING
        }

        @SuppressLint("SetTextI18n")
        fun bind(result: ItemInfo) {
            source = result.source
            itemID = result.id

            binding.root.progress = 0

            binding.thumbnail.controller = Fresco.newDraweeControllerBuilder()
                .setUri(result.thumbnail)
                .setOldController(binding.thumbnail.controller)
                .setControllerListener(controllerListener)
                .build()
            
            binding.title.text = result.title
            binding.idView.text = result.id

            binding.artist.visibility = if (result.artists.isEmpty()) View.GONE else View.VISIBLE

            binding.artist.text = result.artists

            CoroutineScope(Dispatchers.Main).launch {
                with (binding.tagGroup) {
                    tags.clear()
                    result.extra[ItemInfo.ExtraType.TAGS]?.await()?.split(", ")?.map {
                        Tag.parse(it)
                    }?.let { tags.addAll(it) }
                    refresh()
                }
            }

            val extraType = listOf(
                ItemInfo.ExtraType.SERIES,
                ItemInfo.ExtraType.TYPE,
                ItemInfo.ExtraType.LANGUAGE
            )

            CoroutineScope(Dispatchers.Main).launch {
                result.extra[ItemInfo.ExtraType.GROUP]?.await()?.let {
                    if (it.isNotEmpty())
                        binding.artist.text = "${result.artists} ($it)"
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                binding.extra.text =
                    result.extra.entries.filter { it.key in extraType && it.value.await() != null }.fold(StringBuilder()) { res, entry ->
                        entry.value.await()?.let {
                            if (it.isNotEmpty()) {
                                res.append(
                                    itemView.context.getString(
                                        ItemInfo.extraTypeMap[entry.key] ?: error(""),
                                        entry.value.await()
                                    )
                                )
                                res.append('\n')
                            }
                        }
                        res
                    }
            }

            CoroutineScope(Dispatchers.Main).launch {
                binding.pagecount.text = result.extra[ItemInfo.ExtraType.PAGECOUNT]?.let {
                    itemView.context.getString(
                        ItemInfo.extraTypeMap[ItemInfo.ExtraType.PAGECOUNT] ?: error(""),
                        it.await()
                    )
                } ?: "-"
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