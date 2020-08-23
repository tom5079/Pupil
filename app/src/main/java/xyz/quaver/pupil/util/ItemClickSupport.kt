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

package xyz.quaver.pupil.util

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.pupil.R

class ItemClickSupport(private val recyclerView: RecyclerView) {

    var onItemClickListener: ((RecyclerView, Int, View) -> Unit)? = null
    var onItemLongClickListener: ((RecyclerView, Int, View) -> Boolean)? = null

    init {
        recyclerView.apply {
            setTag(R.id.item_click_support, this)
            addOnChildAttachStateChangeListener(object: RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    onItemClickListener?.let { listener ->
                        view.setOnClickListener {
                            recyclerView.getChildViewHolder(view).let { holder ->
                                listener.invoke(recyclerView, holder.adapterPosition, view)
                            }
                        }
                    }
                    onItemLongClickListener?.let { listener ->
                        view.setOnLongClickListener {
                            recyclerView.getChildViewHolder(view).let { holder ->
                                listener.invoke(recyclerView, holder.adapterPosition, view)
                            }
                        }
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    // Do Nothing
                }
            })
        }
    }

    fun detach() {
        recyclerView.apply {
            clearOnChildAttachStateChangeListeners()
            setTag(R.id.item_click_support, null)
        }
    }

    companion object {
        fun addTo(view: RecyclerView) = view.let { removeFrom(it); ItemClickSupport(it) }
        fun removeFrom(view: RecyclerView) = (view.tag as? ItemClickSupport)?.detach()
    }
}