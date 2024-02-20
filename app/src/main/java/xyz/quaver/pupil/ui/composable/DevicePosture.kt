package xyz.quaver.pupil.ui.composable

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed interface DevicePosture {
    data object NormalPosture: DevicePosture

    data class BookPosture(
        val hingePosition: Rect
    ): DevicePosture

    data class Separating(
        val hingePosition: Rect,
        val orientation: FoldingFeature.Orientation
    ): DevicePosture
}

@OptIn(ExperimentalContracts::class)
fun isBookPosture(foldingFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldingFeature != null) }

    return foldingFeature?.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
}

@OptIn(ExperimentalContracts::class)
fun isSeparating(foldingFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldingFeature != null) }

    return foldingFeature?.state == FoldingFeature.State.FLAT && foldingFeature.isSeparating
}