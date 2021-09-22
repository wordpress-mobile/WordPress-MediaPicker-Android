package org.wordpress.android.mediapicker.loader

import org.wordpress.android.mediapicker.model.MediaItem

interface MediaSource {
    suspend fun load(
        forced: Boolean = false,
        loadMore: Boolean = false,
        filter: String? = null
    ): MediaLoadingResult

    sealed class MediaLoadingResult(open val data: List<MediaItem>) {

        data class Success(override val data: List<MediaItem>, val hasMore: Boolean = false) : MediaLoadingResult(data)

        data class Empty(
            val title: String,
            val htmlSubtitle: String? = null,
            val image: Int? = null,
            val bottomImage: Int? = null,
            val bottomImageContentDescription: String? = null
        ) : MediaLoadingResult(listOf())

        data class Failure(
            val title: String,
            val htmlSubtitle: String? = null,
            val image: Int? = null,
            override val data: List<MediaItem> = listOf()
        ) : MediaLoadingResult(data)
    }
}
