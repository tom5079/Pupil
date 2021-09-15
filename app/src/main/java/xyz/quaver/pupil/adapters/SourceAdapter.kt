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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.pupil.databinding.SourceSelectDialogItemBinding
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.SourceEntries

class SourceAdapter(sources: SourceEntries) : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

    var onSourceSelectedListener: ((String) -> Unit)? = null
    var onSourceSettingsSelectedListener: ((String) -> Unit)? = null

    private val sources = sources.toList()

    inner class ViewHolder(private val binding: SourceSelectDialogItemBinding) : RecyclerView.ViewHolder(binding.root) {
        lateinit var source: Source

        init {
            binding.go.setOnClickListener {
                onSourceSelectedListener?.invoke(source.name)
            }
            binding.settings.setOnClickListener {
                onSourceSettingsSelectedListener?.invoke(source.name)
            }
        }

        fun bind(source: Source) {
            this.source = source

            // TODO: save image somewhere else
            binding.icon.setImageResource(source.iconResID)
            binding.name.text = source.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(SourceSelectDialogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sources[position].second)
    }

    override fun getItemCount(): Int = sources.size
}