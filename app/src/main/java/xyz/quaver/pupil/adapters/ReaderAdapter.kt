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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.facebook.drawee.view.SimpleDraweeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.GalleryInfo
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ReaderItemBinding
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.downloader.Cache
import kotlin.math.roundToInt

class ReaderAdapter(
    private val activity: ReaderActivity,
    private val galleryID: String
) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {
    var reader: GalleryInfo? = null

    var isFullScreen = false

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
        }

        fun bind(position: Int) {
            if (cache == null)
                cache = Cache.getInstance(itemView.context, galleryID)

            if (!isFullScreen) {
                binding.root.setBackgroundResource(R.drawable.reader_item_boundary)
                binding.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = 0
                    dimensionRatio =
                        "${reader!!.files[position].width}:${reader!!.files[position].height}"
                }
            } else {
                binding.root.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                binding.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = ConstraintLayout.LayoutParams.MATCH_PARENT
                    dimensionRatio = null
                }
                binding.root.background = null
            }

            binding.readerIndex.text = (position+1).toString()

            val image = cache!!.getImage(position)
            val progress = activity.downloader?.progress?.get(galleryID)?.get(position)

            if (progress?.isInfinite() == true && image != null) {
                binding.progressGroup.visibility = View.INVISIBLE
                binding.image.showImage(image.uri)
            } else {
                binding.progressGroup.visibility = View.VISIBLE
                binding.readerItemProgressbar.progress =
                    if (progress?.isInfinite() == true)
                        100
                    else
                        progress?.roundToInt() ?: 0

                clear()

                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    notifyItemChanged(position)
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

    private var cache: Cache? = null
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount() = reader?.files?.size ?: 0

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
    }

}