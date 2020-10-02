package org.wordpress.android.mediapicker.loader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.utils.MediaUtils
import org.wordpress.android.fluxc.utils.MimeTypes
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.mediapicker.MediaItem
import org.wordpress.android.mediapicker.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.MediaType
import org.wordpress.android.mediapicker.MediaType.AUDIO
import org.wordpress.android.mediapicker.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult
import org.wordpress.android.util.LocaleManagerWrapper
import javax.inject.Inject
import javax.inject.Named

class DeviceListBuilder(
    private val localeManagerWrapper: LocaleManagerWrapper,
    private val deviceMediaLoader: DeviceMediaLoader,
    @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val mediaTypes: Set<MediaType>,
    private val pageSize: Int
) : MediaSource {
    private val mimeTypes = MimeTypes()
    private val cache = mutableMapOf<MediaType, Result>()

    override suspend fun load(
        forced: Boolean,
        loadMore: Boolean,
        filter: String?
    ): MediaLoadingResult {
        if (!loadMore) {
            cache.clear()
        }
        val lowerCaseFilter = filter?.toLowerCase(localeManagerWrapper.getLocale())
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
            val lastShownTimestamp = results.fold(0L) { timestamp, (_, result) ->
                val nextTimestamp = result?.nextTimestamp
                if (nextTimestamp != null && nextTimestamp > timestamp) {
                    nextTimestamp
                } else {
                    timestamp
                }
            }
            results.forEach { (mediaType, result) ->
                if (result != null) {
                    val visibleItems = result.items.takeWhile { it.dataModified > lastShownTimestamp }
                    cache[mediaType] = result.copy(visibleItems = visibleItems.size)
                    mediaItems.addAll(visibleItems)
                }
            }
            mediaItems.sortByDescending { it.dataModified }
            MediaLoadingResult.Success(mediaItems, lastShownTimestamp > 0L)
        }
    }

    private fun loadMedia(mediaType: MediaType, filter: String?): Result? {
        if (!cache[mediaType].shouldLoadMoreData()) {
            return cache[mediaType]
        }
        val lastDateModified = cache[mediaType]?.nextTimestamp
        val deviceMediaList = deviceMediaLoader.loadMedia(mediaType, filter, pageSize, lastDateModified)
        val result = deviceMediaList.items.mapNotNull {
            val mimeType = deviceMediaLoader.getMimeType(it.uri)
            if (MediaUtils.isSupportedMimeType(mimeType)) {
                MediaItem(LocalUri(it.uri), it.uri.toString(), it.title, mediaType, mimeType, it.dateModified)
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
            val mimeType = deviceMediaLoader.getMimeType(document.uri)
            if (mimeType != null && mimeTypes.isSupportedApplicationType(mimeType)) {
                MediaItem(
                        LocalUri(document.uri),
                        document.uri.toString(),
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

    private fun Result?.shouldLoadMoreData(): Boolean {
        return this == null || (nextTimestamp != null && this.items.size <= (visibleItems + pageSize))
    }

    class DeviceListBuilderFactory
    @Inject constructor(
        private val localeManagerWrapper: LocaleManagerWrapper,
        private val deviceMediaLoader: DeviceMediaLoader,
        @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
    ) {
        fun build(mediaTypes: Set<MediaType>): DeviceListBuilder {
            return DeviceListBuilder(
                    localeManagerWrapper,
                    deviceMediaLoader,
                    bgDispatcher,
                    mediaTypes,
                    PAGE_SIZE
            )
        }

        companion object {
            private const val PAGE_SIZE = 32
        }
    }
}
