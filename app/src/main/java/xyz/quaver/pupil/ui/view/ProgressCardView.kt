package xyz.quaver.pupil.ui.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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