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

package xyz.quaver.pupil.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.MirrorAdapter

class MirrorDialog(context: Context) : AlertDialog(context) {

    class ItemTouchHelperCallback : ItemTouchHelper.Callback() {

        var onMoveItem : ((Int, Int) -> (Unit))? = null

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            onMoveItem?.invoke(viewHolder.adapterPosition, target.adapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.settings_mirror_title)
        setView(build())
        setButton(Dialog.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ -> }

        super.onCreate(savedInstanceState)
    }

    private fun build() : View {
        return RecyclerView(context).apply recyclerview@{
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            layoutManager = LinearLayoutManager(context)
            adapter = MirrorAdapter(context).apply adapter@{
                val itemTouchHelper = ItemTouchHelper(ItemTouchHelperCallback().apply {
                    onMoveItem = this@adapter.onItemMove
                }).apply {
                    attachToRecyclerView(this@recyclerview)
                }

                onStartDrag = {
                    itemTouchHelper.startDrag(it)
                }

                onItemMoved = {
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putString("mirrors", it.joinToString(">"))
                        .apply()
                }
            }
        }
    }

}