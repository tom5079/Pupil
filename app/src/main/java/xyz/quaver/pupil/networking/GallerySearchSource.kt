package xyz.quaver.pupil.networking

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class GallerySearchSource(val query: SearchQuery?) {
    private var searchResult: List<Int>? = null
    private var job: Job? = null

    suspend fun load(range: IntRange): Result<Pair<List<GalleryInfo>, Int>> = runCatching {
        val searchResult = searchResult ?: (
            HitomiHttpClient
                .search(query)
                .getOrThrow()
                .toList()
                .also { searchResult = it }
        )

        val galleryResults = coroutineScope {
            searchResult.slice(range).map { galleryID ->
                async {
                    HitomiHttpClient.getGalleryInfo(galleryID)
                }
            }
        }

        val galleries = galleryResults.map { result ->
            result.await().getOrThrow()
        }

        Pair(galleries, searchResult.size)
    }

    fun cancel() = job?.cancel()
}