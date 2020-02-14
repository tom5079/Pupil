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
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.download.Cache
import xyz.quaver.pupil.util.download.DownloadWorker
import java.io.File
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class ReaderAdapter(private val context: Context,
                    private val galleryID: Int) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    val glide = Glide.with(context)

    //region Glide.RecyclerView
    val sizeProvider = ListPreloader.PreloadSizeProvider<File> { _, _, position ->
        Cache(context).getReaderOrNull(galleryID)?.galleryInfo?.getOrNull(position)?.let {
            arrayOf(it.width, it.height).toIntArray()
        }
    }
    val modelProvider = object: ListPreloader.PreloadModelProvider<File> {
        override fun getPreloadItems(position: Int): MutableList<File> {
            return listOf(Cache(context).getImages(galleryID)?.get(position)).filterNotNullTo(mutableListOf())
        }

        override fun getPreloadRequestBuilder(item: File): RequestBuilder<*>? {
            return glide
                .load(item)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .error(R.drawable.image_broken_variant)
                .apply {
                    if (BuildConfig.CENSOR)
                        override(5, 8)
                }
        }
    }
    val preloader = RecyclerViewPreloader<File>(glide, modelProvider, sizeProvider, 10)
    //endregion

    var reader: Reader? = null
    val timer = Timer()

    var isFullScreen = false

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

        if (isFullScreen) {
            holder.view.layoutParams.height = RecyclerView.LayoutParams.MATCH_PARENT
            holder.view.container.layoutParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        } else {
            holder.view.layoutParams.height = RecyclerView.LayoutParams.WRAP_CONTENT
            holder.view.container.layoutParams.height = 0
        }

        holder.view.image.setOnPhotoTapListener { _, _, _ ->
            onItemClickListener?.invoke(position)
        }

        holder.view.container.setOnClickListener {
            onItemClickListener?.invoke(position)
        }

        if (!isFullScreen)
            (holder.view.container.layoutParams as ConstraintLayout.LayoutParams)
                .dimensionRatio = "${reader!!.galleryInfo[position].width}:${reader!!.galleryInfo[position].height}"

        holder.view.reader_index.text = (position+1).toString()

        val images = Cache(context).getImages(galleryID)

        if (images?.get(position) != null) {
            glide
                .load(images[position])
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .error(R.drawable.image_broken_variant)
                .dontTransform()
                .apply {
                    if (BuildConfig.CENSOR)
                        override(5, 8)
                }
                .into(holder.view.image)
        } else {
            val progress = DownloadWorker.getInstance(context).progress[galleryID]?.get(position)

            if (progress?.isNaN() == true) {
                if (Fabric.isInitialized())
                    Crashlytics.logException(DownloadWorker.getInstance(context).exception[galleryID]?.get(position))

                glide
                    .load(R.drawable.image_broken_variant)
                    .into(holder.view.image)

                return
            } else {
                holder.view.reader_item_progressbar.progress =
                    if (progress?.isInfinite() == true)
                        100
                    else
                        progress?.roundToInt() ?: 0

                holder.view.image.setImageDrawable(null)
            }

            timer.schedule(1000) {
                CoroutineScope(Dispatchers.Main).launch {
                    notifyItemChanged(position)
                }
            }
        }
    }

    override fun getItemCount() = reader?.galleryInfo?.size ?: 0

}