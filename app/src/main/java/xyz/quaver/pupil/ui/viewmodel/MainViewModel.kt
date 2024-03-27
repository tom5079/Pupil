package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.quaver.pupil.networking.GalleryInfo
import xyz.quaver.pupil.networking.GallerySearchSource
import xyz.quaver.pupil.networking.SearchQuery
import kotlin.math.max
import kotlin.math.min

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _uiState
    private var searchSource: GallerySearchSource = GallerySearchSource(null)
    private var job: Job? = null

    fun closeDetailScreen() {
        _uiState.value = _uiState.value.copy(
            isDetailOnlyOpen = false
        )
    }

    fun onQueryChange(query: SearchQuery?) {
        _uiState.value = _uiState.value.copy(
            query = query,
            galleryCount = null,
            currentRange = IntRange.EMPTY
        )

        searchSource = GallerySearchSource(query)
    }

    fun loadSearchResult(range: IntRange) {
        job?.cancel()
        job = viewModelScope.launch {
            val sanitizedRange = max(range.first, 0) .. min(range.last, searchState.value.galleryCount ?: Int.MAX_VALUE)
            _uiState.value = _uiState.value.copy(
                loading = true,
                currentRange = sanitizedRange
            )

            var error = false
            val (galleries, galleryCount) = searchSource.load(range).getOrElse {
                error = true
                it.printStackTrace()
                emptyList<GalleryInfo>() to 0
            }

            _uiState.value = _uiState.value.copy(
                galleries = galleries,
                galleryCount = galleryCount,
                error = error,
                loading = false
            )
        }
    }

    fun navigateToDetail() {

    }
}

data class SearchState(
    val query: SearchQuery? = null,
    val galleries: List<GalleryInfo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val galleryCount: Int? = null,
    val currentRange: IntRange = IntRange.EMPTY,
    val openedGallery: GalleryInfo? = null,
    val isDetailOnlyOpen: Boolean = false
)