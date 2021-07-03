/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021  tom5079
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

package xyz.quaver.pupil.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.kodein.di.DIAware
import org.kodein.di.android.x.closestDI
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.util.source

class GalleryDialogViewModel(app: Application) : AndroidViewModel(app), DIAware {

    override val di by closestDI()

    private val _info = MutableLiveData<ItemInfo>()
    val info: LiveData<ItemInfo> = _info

    private val _related = MutableLiveData<List<ItemInfo>>()
    val related: LiveData<List<ItemInfo>> = _related

    fun load(source: String, itemID: String) {
        val source: Source by source(source)

        viewModelScope.launch {
            _info.value = withContext(Dispatchers.IO) {
                source.info(itemID).also { it.awaitAll() }
            }.also {
                _related.value = it.extra[ItemInfo.ExtraType.RELATED_ITEM]?.await()?.split(", ")?.map { related ->
                    async(Dispatchers.IO) {
                        source.info(related)
                    }
                }?.awaitAll()
            }
        }
    }

}