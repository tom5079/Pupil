package xyz.quaver.pupil.ui.composable

import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

enum class FloatingActionButtonState(val isExpanded: Boolean) {
    COLLAPSED(false), EXPANDED(true);

    operator fun not() = lookupTable[!this.isExpanded]!!

    companion object {
        private val lookupTable = mapOf(
            false to COLLAPSED,
            true to EXPANDED
        )
    }
}

data class SubFabItem(
    val icon: ImageVector,
    val label: String? = null
)

@Preview
@Composable
fun MultipleFloatingActionButton(
    items: List<SubFabItem>,
    fabIcon: ImageVector = Icons.Default.Add,
    onItemClicked: () -> Unit = {  },
    targetState: FloatingActionButtonState = FloatingActionButtonState.COLLAPSED,
    onStateChanged: ((FloatingActionButtonState) -> Unit)? = null
) {
    val transition = updateTransition(targetState = targetState, label = "expand")

    Column {
        FloatingActionButton(onClick = {
            onStateChanged?.invoke(!targetState)
        }) {
            items.forEach {

            }
            Icon(imageVector = fabIcon, contentDescription = null)
        }
    }
}