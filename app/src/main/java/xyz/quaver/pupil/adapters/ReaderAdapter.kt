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

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.facebook.drawee.view.SimpleDraweeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ReaderItemBinding
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.Downloader
import kotlin.math.roundToInt

class ReaderAdapter(
    private val context: Context,
    private val source: String,
    private val itemID: String
) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {
    var onItemClickListener : (() -> (Unit))? = null

    inner class ViewHolder(private val binding: ReaderItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            with (binding.image) {
                setFailureImage(ContextCompat.getDrawable(itemView.context, R.drawable.image_broken_variant))
                setOnClickListener {
                    onItemClickListener?.invoke()
                }
            }

            binding.root.setOnClickListener {
                onItemClickListener?.invoke()
            }

            binding.readerItemProgressbar.max = 100
        }

        fun bind(position: Int) {
            binding.readerIndex.text = (position+1).toString()

            val image = Cache.getInstance(context, source, itemID).getImage(position)?.uri

            if (image != null)
                binding.image.showImage(image)
            else {
                val progress = Downloader.getInstance(context).getProgress(source, itemID)?.get(position) ?: 0F

                if (progress == Float.NEGATIVE_INFINITY)
                    with (binding.image) {
                        showImage(Uri.EMPTY)

                        setOnClickListener {
                            if (Downloader.getInstance(context).getProgress(source, itemID)?.get(position) == Float.NEGATIVE_INFINITY)
                                Downloader.getInstance(context).retry(source, itemID)
                        }
                    }
                else {
                    binding.readerItemProgressbar.progress = progress.roundToInt()

                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        notifyItemChanged(position)
                    }
                }
            }
        }

        fun clear() {
            binding.image.mainView.let {
                when (it) {
                    is SubsamplingScaleImageView ->
                        it.recycle()
                    is SimpleDraweeView ->
                        it.controller = null
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ReaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount() = Downloader.getInstance(context).getProgress(source, itemID)?.size ?: 0

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
    }

}