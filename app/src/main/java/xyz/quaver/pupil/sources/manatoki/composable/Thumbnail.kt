/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2021 tom5079
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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.sources.manatoki.composable

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class Thumbnail(
    val itemID: String,
    val title: String,
    val thumbnail: String
): Parcelable

@Composable
fun Thumbnail(
    thumbnail: Thumbnail,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = { }
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 8.dp,
        modifier = modifier.clickable { onClick(thumbnail.itemID) }
    ) {
        Box(
            modifier = Modifier.width(IntrinsicSize.Min)
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = rememberImagePainter(thumbnail.thumbnail),
                contentDescription = null
            )

            Text(
                thumbnail.title,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(8.dp),
                softWrap = true,
                style = MaterialTheme.typography.subtitle1
            )
        }
    }
}