/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
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

import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

class ThumbnailPageAdapter(private val thumbnails: List<String>) : RecyclerView.Adapter<ThumbnailPageAdapter.ViewHolder>() {

    class ViewHolder(val view: RecyclerView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(RecyclerView(parent.context).apply {
            this.layoutManager = GridLayoutManager(parent.context, 3)
            this.adapter = ThumbnailAdapter(listOf())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.view.adapter as ThumbnailAdapter).apply {
            thumbnails = this@ThumbnailPageAdapter.thumbnails.slice(9*position until min(9*position+9, this@ThumbnailPageAdapter.thumbnails.size))
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = if (thumbnails.isEmpty()) 0 else thumbnails.size/9 + if (thumbnails.size%9 != 0) 1 else 0

}