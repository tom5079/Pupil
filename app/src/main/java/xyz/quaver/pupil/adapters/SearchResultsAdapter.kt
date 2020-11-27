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
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.interfaces.SwipeAdapterInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.SearchResultItemBinding
import xyz.quaver.pupil.sources.SearchResult
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.ui.view.ProgressCardView
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadManager
import xyz.quaver.pupil.util.wordCapitalize

class SearchResultsAdapter(private val results: List<SearchResult>) : RecyclerSwipeAdapter<SearchResultsAdapter.ViewHolder>(), SwipeAdapterInterface {

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()
    var onDownloadClickedHandler: ((String) -> Unit)? = null
    var onDeleteClickedHandler: ((String) -> Unit)? = null

    inner class ViewHolder(private val binding: SearchResultItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var itemID: String = ""
        var update = true

        init {
            CoroutineScope(Dispatchers.Main).launch {
                while (update) {
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
        }

        fun updateProgress() = CoroutineScope(Dispatchers.Main).launch {
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

        fun bind(result: SearchResult) {
            itemID = result.id

            binding.thumbnail.ssiv?.recycle()
            binding.thumbnail.showImage(Uri.parse(result.thumbnail))

            updateProgress()

            binding.title.text = result.title
            binding.idView.text = result.id
            binding.artist.text = result.artists.joinToString { it.wordCapitalize() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(SearchResultItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        mItemManger.bindView(holder.itemView, position)
        holder.bind(results[position])
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.update = false
    }

    override fun getItemCount(): Int = results.size

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.swipe_layout

}