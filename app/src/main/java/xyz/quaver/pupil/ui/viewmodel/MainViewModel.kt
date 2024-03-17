package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.observeOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.GallerySearchSource
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.composable.MainDestination
import xyz.quaver.pupil.ui.composable.mainDestinations

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUIState())
    val uiState: StateFlow<MainUIState> = _uiState
    private var searchSource: GallerySearchSource = GallerySearchSource(null)
    private var job: Job? = null

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

    fun onQueryChange(query: SearchQuery?) {
        _uiState.value = _uiState.value.copy(
            query = query,
            validRange = IntRange.EMPTY,
            currentRange = IntRange.EMPTY
        )

        searchSource = GallerySearchSource(query)
    }

    fun loadSearchResult(range: IntRange) {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                currentRange = range
            )

            var error = false
            val (galleries, galleryCount) = searchSource.load(range).getOrElse {
                error = true
                it.printStackTrace()
                emptyList<GalleryInfo>() to 0
            }

            _uiState.value = _uiState.value.copy(
                galleries = galleries,
                validRange = IntRange(1, galleryCount),
                error = error,
                loading = false
            )
        }
    }

    fun navigateToDetail() {

    }
}

data class MainUIState(
    val currentDestination: MainDestination = mainDestinations.first(),
    val query: SearchQuery? = null,
    val galleries: List<GalleryInfo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val validRange: IntRange = IntRange.EMPTY,
    val currentRange: IntRange = IntRange.EMPTY,
    val openedGallery: GalleryInfo? = null,
    val isDetailOnlyOpen: Boolean = false
)