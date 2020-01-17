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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.item_reader.view.*
import kotlinx.coroutines.runBlocking
import xyz.quaver.hitomi.Reader
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.util.GalleryDownloader
import xyz.quaver.pupil.util.getCachedGallery
import java.io.File

class ReaderAdapter(private val glide: RequestManager,
                    private val galleryID: Int,
                    private val images: List<String>) : RecyclerView.Adapter<ReaderAdapter.ViewHolder>() {

    var isFullScreen = false

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

        var reader: Reader? = null
        with (GalleryDownloader[galleryID]?.reader) {
            if (this?.isCompleted == true)
                runBlocking {
                    reader = await()
                }
        }

        glide
            .load(File(getCachedGallery(holder.view.context, galleryID), images[position]))
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .error(R.drawable.image_broken_variant)
            .apply {
                if (BuildConfig.CENSOR)
                    override(5, 8)
                else {
                    val galleryInfo = reader?.galleryInfo?.get(position)

                    if (galleryInfo != null) {
                        (holder.view.image.layoutParams as ConstraintLayout.LayoutParams)
                            .dimensionRatio = "${galleryInfo.width}:${galleryInfo.height}"
                    }
                }
            }
            .into(holder.view.image)
    }

    override fun getItemCount() = images.size

}