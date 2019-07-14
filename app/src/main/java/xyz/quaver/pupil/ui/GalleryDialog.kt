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

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_galleryblock.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.quaver.hitomi.getGallery
import xyz.quaver.pupil.R

class GalleryDialog(context: Context, private val galleryID: Int) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_galleryblock)

        window?.attributes.apply {
            this ?: return@apply

            width = LinearLayout.LayoutParams.MATCH_PARENT
            height = LinearLayout.LayoutParams.MATCH_PARENT
        }

        gallery_fab.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_right))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gallery = getGallery(galleryID)

                launch(Dispatchers.Main) {
                    gallery_toolbar.title = gallery.title

                    Glide.with(context)
                        .load(gallery.thumbnails[0])
                        .into(gallery_thumbnail)
                }
            } catch (e: Exception) {
                Snackbar.make(gallery_layout, R.string.unable_to_connect, Snackbar.LENGTH_INDEFINITE).show()
            }
        }
    }

}