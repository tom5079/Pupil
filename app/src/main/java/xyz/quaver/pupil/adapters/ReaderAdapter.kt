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
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.facebook.drawee.view.SimpleDraweeView
import com.github.piasy.biv.view.FrescoImageViewFactory
import com.github.piasy.biv.view.ImageShownCallback
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.downloader.Cache
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class ReaderAdapter(private val activity: ReaderActivity,
                    private val galleryID: Int) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var reader: Reader? = null
    val timer = Timer()

    var isFullScreen = false

    var onItemClickListener : (() -> (Unit))? = null

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun clear() {
            view.image.mainView.let {
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
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_reader, parent, false
        ).let {
            with (it) {
                image.setImageViewFactory(FrescoImageViewFactory())
                image.setImageShownCallback(object: ImageShownCallback {
                    override fun onThumbnailShown() {}
                    override fun onMainImageShown() {
                        placeholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            dimensionRatio = null
                        }
                    }
                })
                image.setOnClickListener {
                    onItemClickListener?.invoke()
                }
                setOnClickListener {
                    onItemClickListener?.invoke()
                }
            }

            ViewHolder(it)
        }
    }

    private var cache: Cache? = null
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view as ConstraintLayout

        if (cache == null)
            cache = Cache.getInstance(holder.view.context, galleryID)

        holder.view.layoutParams.height =
            if (isFullScreen)
                ConstraintLayout.LayoutParams.MATCH_PARENT
            else
                ConstraintLayout.LayoutParams.WRAP_CONTENT

        holder.view.image.layoutParams.height =
            if (isFullScreen)
                ConstraintLayout.LayoutParams.MATCH_PARENT
            else
                ConstraintLayout.LayoutParams.WRAP_CONTENT

        holder.view.placeholder.updateLayoutParams<ConstraintLayout.LayoutParams> {
            dimensionRatio = "${reader!!.galleryInfo.files[position].width}:${reader!!.galleryInfo.files[position].height}"
        }

        holder.view.reader_index.text = (position+1).toString()

        val image = cache!!.getImage(position)
        val progress = activity.downloader?.progress?.get(galleryID)?.get(position)

        if (progress?.isInfinite() == true && image != null) {
            holder.view.progress_group.visibility = View.INVISIBLE
            holder.view.image.showImage(image.uri)
        } else {
            holder.view.progress_group.visibility = View.VISIBLE
            holder.view.reader_item_progressbar.progress =
                if (progress?.isInfinite() == true)
                    100
                else
                    progress?.roundToInt() ?: 0

            holder.clear()

            timer.schedule(1000) {
                CoroutineScope(Dispatchers.Main).launch {
                    notifyItemChanged(position)
                }
            }
        }
    }

    override fun getItemCount() = reader?.galleryInfo?.files?.size ?: 0

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clear()
    }

}