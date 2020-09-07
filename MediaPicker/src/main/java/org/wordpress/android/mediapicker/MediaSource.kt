package org.wordpress.android.mediapicker

interface MediaSource {
    suspend fun load(
        mediaTypes: Set<MediaType>,
        forced: Boolean = false,
        loadMore: Boolean = false
    ): MediaLoadingResult

    sealed class MediaLoadingResult {
        data class Success(val mediaItems: List<MediaItem>, val hasMore: Boolean = false) : MediaLoadingResult()
        data class Failure(val message: String) : MediaLoadingResult()
    }
}
