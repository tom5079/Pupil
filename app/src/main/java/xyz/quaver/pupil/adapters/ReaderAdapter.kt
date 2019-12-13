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
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
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
        holder.view as ImageView

        glide
            .load(File(getCachedGallery(holder.view.context, galleryID), images[position]))
            .dontTransform()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .error(R.drawable.image_broken_variant)
            .apply {
                if (BuildConfig.CENSOR)
                    override(5, 8)
            }
            .fitCenter()
            .into(holder.view)
    }

    override fun getItemCount() = images.size

}