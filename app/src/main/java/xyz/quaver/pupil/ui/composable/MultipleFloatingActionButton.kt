package xyz.quaver.pupil.ui.composable

import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

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

@Composable
fun MiniFloatingActionButton(
    modifier: Modifier = Modifier,
    item: SubFabItem,
    onClick: ((SubFabItem) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val elevation = FloatingActionButtonDefaults.elevation()
        val interactionSource = remember { MutableInteractionSource() }

        item.label?.let { label ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                elevation = elevation.elevation(interactionSource).value
            ) {
                Text(modifier = Modifier.padding(4.dp), text = label)
            }
        }

        FloatingActionButton(
            modifier = Modifier.size(32.dp),
            onClick = { onClick?.invoke(item) },
            elevation = elevation,
            interactionSource = interactionSource
        ) {
            Icon(item.icon, contentDescription = null)
        }
    }
}

private class FloatingActionButtonItemProvider : PreviewParameterProvider<SubFabItem> {
    override val values: Sequence<SubFabItem>
        get() = sequenceOf(
            SubFabItem(Icons.Default.PlayArrow, "Play"),
            SubFabItem(Icons.Default.Stop, "Stop")
        )
}

@Preview
@Composable
fun MultipleFloatingActionButton(
    @PreviewParameter(provider = FloatingActionButtonItemProvider::class) items: List<SubFabItem>,
    fabIcon: ImageVector = Icons.Default.Add,
    onItemClick: ((SubFabItem) -> Unit)? = null,
    targetState: FloatingActionButtonState = FloatingActionButtonState.COLLAPSED,
    onStateChanged: ((FloatingActionButtonState) -> Unit)? = null
) {
    val transition = updateTransition(targetState = targetState, label = "expand")

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            MiniFloatingActionButton(modifier = Modifier.padding(end = 12.dp),item = item) {
                onItemClick?.invoke(it)
            }
        }

        FloatingActionButton(onClick = {
            onStateChanged?.invoke(!targetState)
        }) {
            Icon(imageVector = fabIcon, contentDescription = null)
        }
    }
}