package xyz.quaver.pupil.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.ProgressCardViewBinding
import xyz.quaver.pupil.sources.ItemInfo
import xyz.quaver.pupil.sources.Source

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressCardView(progress: Float? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        elevation = 4.dp
    ) {
        Column {
            progress?.run { LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth()) }
            content()
        }
    }
}