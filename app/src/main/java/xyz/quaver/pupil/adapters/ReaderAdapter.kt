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

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.Code
import xyz.quaver.hitomi.Reader
import xyz.quaver.hitomi.getReferer
import xyz.quaver.hitomi.imageUrlFromImage
import xyz.quaver.hiyobi.createImgList
import xyz.quaver.io.util.readBytes
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.services.DownloadService
import xyz.quaver.pupil.ui.ReaderActivity
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

class ReaderAdapter(private val activity: ReaderActivity,
                    private val galleryID: Int) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var reader: Reader? = null
    val timer = Timer()

    private val glide = Glide.with(activity)

    var isFullScreen = false

    var onItemClickListener : ((Int) -> (Unit))? = null

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_reader, parent, false
        ).let {
            ViewHolder(it)
        }
    }

    private var cache: Cache? = null
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view as ConstraintLayout

        if (cache == null)
            cache = Cache.getInstance(holder.view.context, galleryID)

        if (isFullScreen) {
            holder.view.container.layoutParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT
        } else {
            holder.view.container.layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        }

        holder.view.image.setOnPhotoTapListener { _, _, _ ->
            onItemClickListener?.invoke(position)
        }

        holder.view.container.setOnClickListener {
            onItemClickListener?.invoke(position)
        }

        holder.view.reader_index.text = (position+1).toString()

        if (Preferences["cache_disable"]) {
            val lowQuality: Boolean = Preferences["low_quality"]

            val url = when (reader!!.code) {
                Code.HITOMI ->
                    GlideUrl(
                        imageUrlFromImage(
                            galleryID,
                            reader!!.galleryInfo.files[position],
                            !lowQuality
                        )
                    , LazyHeaders.Builder().addHeader("Referer", getReferer(galleryID)).build())
                Code.HIYOBI ->
                    GlideUrl(createImgList(galleryID, reader!!, lowQuality)[position].path)
                else -> null
            }
            holder.view.image.post {
                glide
                    .load(url!!)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .error(R.drawable.image_broken_variant)
                    .apply {
                        if (BuildConfig.CENSOR)
                            override(5, 8)
                        else
                            override(
                                holder.view.context.resources.displayMetrics.widthPixels,
                                holder.view.context.resources.getDimensionPixelSize(R.dimen.reader_max_height)
                            )
                    }
                    .error(R.drawable.image_broken_variant)
                    .into(holder.view.image)
            }
        } else {
            val image = cache!!.getImage(position)
            val progress = activity.downloader?.progress?.get(galleryID)?.get(position)

            if (progress?.isInfinite() == true && image != null) {
                holder.view.reader_item_progressbar.visibility = View.INVISIBLE

                CoroutineScope(Dispatchers.IO).launch {
                    glide
                        .load(image.readBytes())
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .apply {
                            if (BuildConfig.CENSOR)
                                override(5, 8)
                            else
                                override(
                                    holder.view.context.resources.displayMetrics.widthPixels,
                                    holder.view.context.resources.getDimensionPixelSize(R.dimen.reader_max_height)
                                )
                        }
                        .error(R.drawable.image_broken_variant)
                        .listener(object: RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                cache!!.metadata.imageList?.set(position, null)
                                image.delete()
                                DownloadService.cancel(holder.view.context, galleryID)
                                DownloadService.download(holder.view.context, galleryID, true)
                                return true
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean
                            ) = false
                        }).let { launch(Dispatchers.Main) { it.into(holder.view.image) } }
                }
            } else {
                holder.view.reader_item_progressbar.visibility = View.VISIBLE

                glide.clear(holder.view.image)

                holder.view.reader_item_progressbar.progress =
                    if (progress?.isInfinite() == true)
                        100
                    else
                        progress?.roundToInt() ?: 0

                holder.view.image.setImageDrawable(null)

                timer.schedule(1000) {
                    CoroutineScope(Dispatchers.Main).launch {
                        notifyItemChanged(position)
                    }
                }
            }
        }
    }

    override fun getItemCount() = reader?.galleryInfo?.files?.size ?: 0

}