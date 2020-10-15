package org.wordpress.android.mediapicker.loader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.R
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.MediaActionBuilder
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.MediaStore
import org.wordpress.android.fluxc.store.MediaStore.FetchMediaListPayload
import org.wordpress.android.fluxc.store.MediaStore.OnMediaListFetched
import org.wordpress.android.fluxc.utils.MimeType
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.mediapicker.MediaItem
import org.wordpress.android.mediapicker.MediaItem.Identifier.RemoteId
import org.wordpress.android.mediapicker.MediaType
import org.wordpress.android.mediapicker.MediaType.AUDIO
import org.wordpress.android.mediapicker.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Empty
import org.wordpress.android.ui.utils.UiString.UiStringRes
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MediaLibraryDataSource(
    private val mediaStore: MediaStore,
    private val dispatcher: Dispatcher,
    @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val siteModel: SiteModel,
    private val mediaTypes: Set<MediaType>
) : MediaSource {
    init {
        dispatcher.register(this)
    }

    private var loadContinuations = mutableMapOf<MimeType.Type, Continuation<OnMediaListFetched>>()

    override suspend fun load(
        forced: Boolean,
        loadMore: Boolean,
        filter: String?
    ): MediaLoadingResult {
        return withContext(bgDispatcher) {
            val loadingResults = mediaTypes.map { mediaType ->
                async {
                    loadPage(
                            siteModel,
                            loadMore,
                            mediaType.toMimeType()
                    )
                }
            }.map { it.await() }

            var error: String? = null
            var hasMore = false
            for (loadingResult in loadingResults) {
                if (loadingResult.isError) {
                    error = loadingResult.error.message
                    break
                } else {
                    hasMore = hasMore || loadingResult.canLoadMore
                }
            }
            if (error != null) {
                MediaLoadingResult.Failure(error)
            } else {
                val data = get(mediaTypes, filter)
                if (filter.isNullOrEmpty() || data.isNotEmpty()) {
                    MediaLoadingResult.Success(data, hasMore)
                } else {
                    Empty(
                            UiStringRes(R.string.media_empty_search_list),
                            image = R.drawable.img_illustration_empty_results_216dp
                    )
                }
            }
        }
    }

    private suspend fun get(mediaTypes: Set<MediaType>, filter: String?): List<MediaItem> {
        return withContext(bgDispatcher) {
            mediaTypes.map { mediaType ->
                async {
                    if (filter == null) {
                        getFromDatabase(mediaType)
                    } else {
                        searchInDatabase(mediaType, filter)
                    }
                }
            }.fold(mutableListOf<MediaItem>()) { result, databaseItems ->
                result.addAll(databaseItems.await())
                result
            }.sortedByDescending { (it.identifier as? RemoteId)?.value }
        }
    }

    private fun List<MediaModel>.toMediaItems(mediaType: MediaType): List<MediaItem> {
        return this.map { mediaModel ->
            MediaItem(
                    RemoteId(mediaModel.mediaId),
                    mediaModel.url,
                    mediaModel.title,
                    mediaType,
                    mediaModel.mimeType,
                    0
            )
        }
    }

    private fun getFromDatabase(mediaType: MediaType): List<MediaItem> {
        return when (mediaType) {
            IMAGE -> mediaStore.getSiteImages(siteModel)
            VIDEO -> mediaStore.getSiteVideos(siteModel)
            AUDIO -> mediaStore.getSiteAudio(siteModel)
            DOCUMENT -> mediaStore.getSiteDocuments(siteModel)
        }.toMediaItems(mediaType)
    }

    private fun searchInDatabase(mediaType: MediaType, filter: String): List<MediaItem> {
        return when (mediaType) {
            IMAGE -> mediaStore.searchSiteImages(siteModel, filter)
            VIDEO -> mediaStore.searchSiteVideos(siteModel, filter)
            AUDIO -> mediaStore.searchSiteAudio(siteModel, filter)
            DOCUMENT -> mediaStore.searchSiteDocuments(siteModel, filter)
        }.toMediaItems(mediaType)
    }

    private suspend fun loadPage(siteModel: SiteModel, loadMore: Boolean, filter: MimeType.Type): OnMediaListFetched =
            suspendCoroutine { cont ->
                loadContinuations[filter] = cont
                val payload = FetchMediaListPayload(
                        siteModel,
                        NUM_MEDIA_PER_FETCH,
                        loadMore,
                        filter
                )
                dispatcher.dispatch(MediaActionBuilder.newFetchMediaListAction(payload))
            }

    private fun MediaType.toMimeType(): MimeType.Type {
        return when (this) {
            IMAGE -> MimeType.Type.IMAGE
            VIDEO -> MimeType.Type.VIDEO
            AUDIO -> MimeType.Type.AUDIO
            DOCUMENT -> MimeType.Type.APPLICATION
        }
    }

    @Subscribe(threadMode = MAIN)
    fun onMediaListFetched(event: OnMediaListFetched) {
        loadContinuations[event.mimeType]?.resume(event)
        loadContinuations.remove(event.mimeType)
    }

    companion object {
        const val NUM_MEDIA_PER_FETCH = 24
    }

    class MediaLibraryDataSourceFactory
    @Inject constructor(
        private val mediaStore: MediaStore,
        private val dispatcher: Dispatcher,
        @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
    ) {
        fun build(siteModel: SiteModel, mediaTypes: Set<MediaType>) =
                MediaLibraryDataSource(mediaStore, dispatcher, bgDispatcher, siteModel, mediaTypes)
    }
}
