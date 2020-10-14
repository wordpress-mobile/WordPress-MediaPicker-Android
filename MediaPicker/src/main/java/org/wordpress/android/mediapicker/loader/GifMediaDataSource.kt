package org.wordpress.android.mediapicker.loader

import android.content.Context
import com.tenor.android.core.constant.MediaCollectionFormat
import com.tenor.android.core.model.impl.Result
import org.wordpress.android.R

import org.wordpress.android.mediapicker.MediaItem
import org.wordpress.android.mediapicker.MediaItem.Identifier.GifMediaIdentifier
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Failure
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Success
import org.wordpress.android.util.UriUtilsWrapper

import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GifMediaDataSource
@Inject constructor(
    private val context: Context,
    private val tenorClient: TenorGifClient,
    private val uriUtilsWrapper: UriUtilsWrapper
) : MediaSource {
    private var nextPosition: Int = 0
    private val items = mutableListOf<MediaItem>()
    private var lastFilter: String? = null

    override suspend fun load(forced: Boolean, loadMore: Boolean, filter: String?): MediaLoadingResult {
        if (!loadMore) {
            lastFilter = filter
            items.clear()
            nextPosition = 0
        }

        return if (!filter.isNullOrBlank()) {
            suspendCoroutine<MediaLoadingResult> { cont ->
                tenorClient.search(filter,
                        nextPosition,
                        PAGE_SIZE,
                        onSuccess = { response ->
                            val gifList = response.results.map { it.toMediaItem() }

                            items.addAll(gifList)
                            val newPosition = response.next.toIntOrNull() ?: 0
                            val hasMore = newPosition > nextPosition
                            nextPosition = newPosition

                            cont.resume(Success(items.toList(), hasMore))
                        },
                        onFailure = {
                            val errorMessage = it?.message
                                    ?: context.getString(R.string.gif_list_search_returned_unknown_error)
                            cont.resume(Failure(errorMessage))
                        }
                )
            }
        } else {
            Success(listOf(), false)
        }
    }

    private fun Result.toMediaItem() = MediaItem(
            identifier = GifMediaIdentifier(
            null,
            uriUtilsWrapper.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF)),
            title),
            url = uriUtilsWrapper.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF_NANO)).toString(),
            type = IMAGE,
            dataModified = 0
    )

    private fun Result.urlFromCollectionFormat(format: String) =
            medias.firstOrNull()?.get(format)?.url

    companion object {
        private const val PAGE_SIZE = 36
    }
}
