package org.wordpress.android.mediapicker.source.device

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.wordpress.android.mediapicker.api.MediaSource
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult.Empty
import org.wordpress.android.mediapicker.api.MimeTypeProvider
import org.wordpress.android.mediapicker.model.MediaItem
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.model.UiString.UiStringText
import javax.inject.Inject

class DeviceMediaSource(
    private val deviceMediaLoader: DeviceMediaLoader,
    private val bgDispatcher: CoroutineDispatcher,
    private val mediaTypes: Set<MediaType>,
    private val pageSize: Int,
    private val mimeTypeProvider: MimeTypeProvider,
) : MediaSource {
    private val cache = mutableMapOf<MediaType, Result>()

    override suspend fun load(
        forced: Boolean,
        loadMore: Boolean,
        filter: String?
    ): MediaLoadingResult {
        if (!loadMore) {
            cache.clear()
        }
        val lowerCaseFilter = filter?.lowercase()
        return withContext(bgDispatcher) {
            val mediaItems = mutableListOf<MediaItem>()
            val deferredJobs = mediaTypes.map { mediaType ->
                when (mediaType) {
                    IMAGE, VIDEO, AUDIO -> async {
                        mediaType to loadMedia(
                            mediaType,
                            lowerCaseFilter
                        )
                    }
                    DOCUMENT -> async { mediaType to loadDownloads(lowerCaseFilter) }
                }
            }
            val results = deferredJobs.map { it.await() }
            // This is necessary because of the following use case:
            // - Page size is 5, we have 10 new images, 10 audio files that are all older than all the images
            // - We load first 5 images and first five audio files
            // - The 5 other images are newer than the currently loaded audio files
            // - We shouldn't show any audio files until we show all the images
            // The following solution goes through all the results and finds the last item in each list.
            // From these items it picks the newest one (the last one we definitely want to show)
            // This item sets the threshold for the visible items in all the list
            val lastShownTimestamp = results.fold(0L) { timestamp, (_, result) ->
                val nextTimestamp = result?.nextTimestamp
                if (nextTimestamp != null && nextTimestamp > timestamp) {
                    nextTimestamp
                } else {
                    timestamp
                }
            }
            // Here we filter out all the items older than the selected last visible item
            results.forEach { (mediaType, result) ->
                if (result != null) {
                    val visibleItems = result.items.takeWhile { it.dataModified >= lastShownTimestamp }
                    cache[mediaType] = result.copy(visibleItems = visibleItems.size)
                    mediaItems.addAll(visibleItems)
                }
            }
            mediaItems.sortByDescending { it.dataModified }
            if (filter.isNullOrEmpty() || mediaItems.isNotEmpty()) {
                MediaLoadingResult.Success(mediaItems, lastShownTimestamp > 0L)
            } else {
                Empty(UiStringText("No media matching your search"))
            }
        }
    }

    private fun loadMedia(mediaType: MediaType, filter: String?): Result? {
        if (!cache[mediaType].shouldLoadMoreData()) {
            return cache[mediaType]
        }
        val lastDateModified = cache[mediaType]?.nextTimestamp
        val deviceMediaList = deviceMediaLoader.loadMedia(mediaType, filter, pageSize, lastDateModified)
        val result = deviceMediaList.items.mapNotNull {
            val mimeType = deviceMediaLoader.getMimeType(it.mediaUri)
            val isMimeTypeSupported = mimeType != null &&
                mimeTypeProvider.isMimeTypeSupported(mimeType)

            if (isMimeTypeSupported) {
                MediaItem(
                    LocalUri(it.mediaUri),
                    it.mediaUri.toString(),
                    it.title,
                    mediaType,
                    mimeType,
                    it.dateModified
                )
            } else {
                null
            }
        }
        addPage(mediaType, result, deviceMediaList.next)
        return cache[mediaType]
    }

    private suspend fun loadDownloads(filter: String?): Result? = withContext(bgDispatcher) {
        if (!cache[DOCUMENT].shouldLoadMoreData()) {
            return@withContext cache[DOCUMENT]
        }
        val lastDateModified = cache[DOCUMENT]?.nextTimestamp
        val documentsList = deviceMediaLoader.loadDocuments(filter, pageSize, lastDateModified)

        val filteredPage = documentsList.items.mapNotNull { document ->
            val mimeType = deviceMediaLoader.getMimeType(document.mediaUri)
            val isMimeTypeSupported = mimeType != null &&
                mimeTypeProvider.isMimeTypeSupported(mimeType)

            val isSupportedApplicationType = mimeType != null &&
                mimeTypeProvider.isApplicationTypeSupported(mimeType)

            if (isSupportedApplicationType && isMimeTypeSupported) {
                MediaItem(
                    LocalUri(document.mediaUri),
                    document.mediaUri.toString(),
                    document.title,
                    DOCUMENT,
                    mimeType,
                    document.dateModified
                )
            } else {
                null
            }
        }
        addPage(DOCUMENT, filteredPage, documentsList.next)
        return@withContext cache[DOCUMENT]
    }

    private fun addPage(mediaType: MediaType, page: List<MediaItem>, nextTimestamp: Long?) {
        val newData = cache[mediaType]?.items?.toMutableList() ?: mutableListOf()
        newData.addAll(page)
        cache[mediaType] = Result(newData, nextTimestamp)
    }

    data class Result(val items: List<MediaItem>, val nextTimestamp: Long? = null, val visibleItems: Int = 0)

    // We only want to show more data if there isn't already a page loaded that wasn't shown before
    private fun Result?.shouldLoadMoreData(): Boolean {
        return this == null || (nextTimestamp != null && this.items.size <= (visibleItems + pageSize))
    }

    class Factory @Inject constructor(
        private val deviceMediaLoader: DeviceMediaLoader,
        private val bgDispatcher: CoroutineDispatcher,
        private val mimeTypeProvider: MimeTypeProvider,
    ) {
        fun build(mediaTypes: Set<MediaType>): MediaSource {
            return DeviceMediaSource(
                deviceMediaLoader,
                bgDispatcher,
                mediaTypes,
                PAGE_SIZE,
                mimeTypeProvider
            )
        }

        companion object {
            private const val PAGE_SIZE = 32
        }
    }
}
