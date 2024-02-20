package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.composable.MainRoutes

class MainViewModel : ViewModel() {
    val uiState: MainUIState = MainUIState()
}

data class MainUIState(
    val route: MainRoutes = MainRoutes.SEARCH,
    val query: SearchQuery? = null
)