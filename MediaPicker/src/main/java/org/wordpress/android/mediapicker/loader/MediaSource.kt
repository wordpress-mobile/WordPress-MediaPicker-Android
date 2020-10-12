package org.wordpress.android.mediapicker.loader

import org.wordpress.android.mediapicker.MediaItem

interface MediaSource {
    suspend fun load(
        forced: Boolean = false,
        loadMore: Boolean = false,
        filter: String? = null
    ): MediaLoadingResult

    sealed class MediaLoadingResult {
        data class Success(val data: List<MediaItem>, val hasMore: Boolean = false) : MediaLoadingResult()
        data class Failure(val message: String) : MediaLoadingResult()
    }
}
