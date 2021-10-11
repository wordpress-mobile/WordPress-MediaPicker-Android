package org.wordpress.android.mediapicker.source.device

import android.content.Context
import com.tenor.android.core.constant.MediaCollectionFormat
import com.tenor.android.core.model.impl.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.api.MediaSource
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult.*
import org.wordpress.android.mediapicker.model.MediaItem
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.GifMediaId
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.source.device.util.UriUtilsWrapper
import org.wordpress.android.mediapicker.source.gif.R
import org.wordpress.android.mediapicker.util.UiString.UiStringRes
import org.wordpress.android.mediapicker.util.UiString.UiStringText
import org.wordpress.android.util.NetworkUtils
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GifMediaDataSource
@Inject constructor(
    @ApplicationContext private val context: Context,
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

        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Failure(
                    UiStringRes(R.string.no_network_title),
                    htmlSubtitle = UiStringRes(R.string.no_network_message),
                    image = R.drawable.img_illustration_cloud_off_152dp,
                    data = items
            )
        }

        return if (!filter.isNullOrBlank()) {
            suspendCoroutine { cont ->
                tenorClient.search(filter,
                        nextPosition,
                        PAGE_SIZE,
                        onSuccess = { response ->
                            val gifList = response.results.map { it.toMediaItem() }

                            items.addAll(gifList)
                            val newPosition = response.next.toIntOrNull() ?: 0
                            val hasMore = newPosition > nextPosition
                            nextPosition = newPosition
                            val result = if (items.isNotEmpty()) {
                                Success(items.toList(), hasMore)
                            } else {
                                Empty(UiStringRes(R.string.gif_picker_empty_search_list))
                            }
                            cont.resume(result)
                        },
                        onFailure = {
                            val errorMessage = it?.message
                                    ?: context.getString(R.string.gif_list_search_returned_unknown_error)
                            cont.resume(
                                    Failure(
                                            UiStringRes(R.string.media_loading_failed),
                                            htmlSubtitle = UiStringText(errorMessage),
                                            image = R.drawable.img_illustration_cloud_off_152dp,
                                            data = items
                                    )
                            )
                        }
                )
            }
        } else {
            buildDefaultScreen()
        }
    }

    private fun buildDefaultScreen(): MediaLoadingResult {
        val title = UiStringRes(R.string.gif_picker_initial_empty_text)
        return Empty(
                title,
                null,
                R.drawable.img_illustration_media_105dp,
                R.drawable.img_tenor_100dp,
                UiStringRes(R.string.gif_powered_by_tenor)
        )
    }

    private fun Result.toMediaItem() = MediaItem(
            identifier = GifMediaId(
                    uriUtilsWrapper.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF)),
                    title
            ),
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
