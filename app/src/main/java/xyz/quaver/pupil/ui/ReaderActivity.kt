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

package xyz.quaver.pupil.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material.Text
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.accompanist.appcompattheme.AppCompatTheme
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import xyz.quaver.pupil.databinding.ReaderActivityBinding
import xyz.quaver.pupil.ui.viewmodel.ReaderViewModel

class ReaderActivity : BaseActivity(), DIAware {
    override val di by closestDI()

    private var menu: Menu? = null

    private lateinit var binding: ReaderActivityBinding
    private val model: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppCompatTheme {
                Scaffold(
                    topBar = {
                        TopAppBar() {
                            Text("Reader")
                        }
                    }
                ) {

                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.handleIntent(intent)
    }
}