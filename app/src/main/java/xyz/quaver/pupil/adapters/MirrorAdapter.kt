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

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.MirrorsItemBinding
import xyz.quaver.pupil.util.Preferences
import java.util.*

class MirrorAdapter(context: Context) : RecyclerView.Adapter<MirrorAdapter.ViewHolder>() {

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(val binding: MirrorsItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.mirrorButton.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN)
                    onStartDrag?.invoke(this)

                true
            }
        }
        fun bind(mirror: String) {
            binding.mirrorName.text = mirror
        }
    }

    val mirrors = context.resources.getStringArray(R.array.mirrors).map {
        it.split('|').let { split ->
            Pair(split.first(), split.last())
        }
    }.toMap()

    val list = mirrors.keys.toMutableList().apply {
        Preferences.get<String>("mirrors")
            .split(">")
            .asReversed()
            .forEach {
                if (this.contains(it)) {
                    this.remove(it)
                    this.add(0, it)
                }
            }
    }

    val onItemMove : ((Int, Int) -> Unit) = { from, to ->
        Collections.swap(list, from, to)
        notifyItemMoved(from, to)
        onItemMoved?.invoke(list)
    }
    var onStartDrag : ((ViewHolder) -> Unit)? = null
    var onItemMoved : ((List<String>) -> (Unit))? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mirrors[list.elementAt(position)] ?: error(""))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(MirrorsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = mirrors.size

}