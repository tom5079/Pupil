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
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_mirrors.view.*
import xyz.quaver.pupil.R
import java.util.*

class MirrorAdapter(context: Context) : RecyclerView.Adapter<MirrorAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    val mirrors = context.resources.getStringArray(R.array.mirrors).map {
        it.split('|').let { split ->
            Pair(split.first(), split.last())
        }
    }.toMap()

    val list = mirrors.keys.toMutableList().apply {
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("mirrors", "")!!
            .split(">")
            .reversed()
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
        with(holder.view) {
            mirror_name.text = mirrors[list.elementAt(position)]
            mirror_button.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN)
                    onStartDrag?.invoke(holder)

                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_mirrors, parent, false
        ).let {
            ViewHolder(it)
        }
    }

    override fun getItemCount() = mirrors.size

}