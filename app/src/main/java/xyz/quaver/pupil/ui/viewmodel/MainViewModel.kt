package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.composable.MainDestination
import xyz.quaver.pupil.ui.composable.mainDestinations

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUIState())
    val uiState: StateFlow<MainUIState> = _uiState

    fun navigateToDestination(destination: MainDestination) {
        _uiState.value = MainUIState(
            currentDestination = destination
        )
    }

    fun closeDetailScreen() {
        _uiState.value = _uiState.value.copy(
            isDetailOnlyOpen = false
        )
    }

    fun navigateToDetail() {

    }
}

data class MainUIState(
    val currentDestination: MainDestination = mainDestinations.first(),
    val query: SearchQuery? = null,
    val loading: Boolean = true,
    val openedGallery: GalleryInfo? = null,
    val isDetailOnlyOpen: Boolean = false
)