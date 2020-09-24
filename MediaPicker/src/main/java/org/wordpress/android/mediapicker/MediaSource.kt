package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaItem.Identifier

interface MediaSource {
    suspend fun load(
        forced: Boolean = false,
        loadMore: Boolean = false,
        filter: String? = null
    ): MediaLoadingResult

    suspend fun insert(identifiers: List<Identifier>): MediaInsertResult = MediaInsertResult.Success(identifiers)

    sealed class MediaLoadingResult {
        data class Success(val data: List<MediaItem>, val hasMore: Boolean = false) : MediaLoadingResult()
        data class Failure(val message: String) : MediaLoadingResult()
    }

    sealed class MediaInsertResult {
        data class Success(val identifiers: List<Identifier>) : MediaInsertResult()
        data class Failure(val message: String) : MediaInsertResult()
    }
}
