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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.download.Cache
import xyz.quaver.pupil.util.download.DownloadWorker
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class ReaderAdapter(private val context: Context,
                    private val galleryID: Int) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var isFullScreen = false

    var reader: Reader? = null
    private val glide = Glide.with(context)

    var onItemClickListener : ((Int) -> (Unit))? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            reader = Cache(context).getReader(galleryID)
            launch(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_reader, parent, false
        ).let {
            ViewHolder(it)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view as ConstraintLayout

        if (isFullScreen)
            holder.view.layoutParams.height = RecyclerView.LayoutParams.MATCH_PARENT
        else
            holder.view.layoutParams.height = RecyclerView.LayoutParams.WRAP_CONTENT

        holder.view.image.setOnPhotoTapListener { _, _, _ ->
            onItemClickListener?.invoke(position)
        }

        (holder.view.container.layoutParams as ConstraintLayout.LayoutParams)
            .dimensionRatio = "${reader!!.galleryInfo[position].width}:${reader!!.galleryInfo[position].height}"

        holder.view.reader_item_progressbar.progress = DownloadWorker.getInstance(context).progress[galleryID]?.get(position)?.roundToInt() ?: 0
        holder.view.reader_index.text = (position+1).toString()

        val progress = DownloadWorker.getInstance(context).progress[galleryID]?.get(position)
        if (progress?.isFinite() == false) {
            when {
                progress.isInfinite() -> {
                    var image = Cache(context).getImages(galleryID)

                    while (image?.get(position) == null)
                        image = Cache(context).getImages(galleryID)

                    glide
                        .load(image[position])
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .error(R.drawable.image_broken_variant)
                        .apply {
                            if (BuildConfig.CENSOR)
                                override(5, 8)
                        }
                        .into(holder.view.image)
                }
                progress.isNaN() -> {
                    glide
                        .load(R.drawable.image_broken_variant)
                        .into(holder.view.image)
                    Snackbar
                        .make(
                            holder.view,
                            DownloadWorker.getInstance(context).exception[galleryID]!![position]?.message
                                ?: context.getText(R.string.default_error_msg),
                            Snackbar.LENGTH_INDEFINITE
                        )
                        .show()
                }
            }

        } else {
            holder.view.image.setImageDrawable(null)

            Timer().schedule(1000) {
                CoroutineScope(Dispatchers.Main).launch {
                    notifyItemChanged(position)
                }
            }
        }
    }

    override fun getItemCount() = reader?.galleryInfo?.size ?: 0

}