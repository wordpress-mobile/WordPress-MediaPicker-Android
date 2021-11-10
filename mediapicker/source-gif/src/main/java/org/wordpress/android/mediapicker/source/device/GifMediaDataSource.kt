package org.wordpress.android.mediapicker.source.device

import android.content.Context
import com.tenor.android.core.constant.MediaCollectionFormat
import com.tenor.android.core.model.impl.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.api.MediaSource
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult.Empty
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult.Failure
import org.wordpress.android.mediapicker.api.MediaSource.MediaLoadingResult.Success
import org.wordpress.android.mediapicker.api.R.drawable
import org.wordpress.android.mediapicker.model.MediaItem
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.GifMediaId
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.UiString.UiStringRes
import org.wordpress.android.mediapicker.model.UiString.UiStringText
import org.wordpress.android.mediapicker.source.gif.R
import org.wordpress.android.mediapicker.source.gif.R.string
import org.wordpress.android.util.NetworkUtils
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.wordpress.android.mediapicker.api.R as ApiR

class GifMediaDataSource
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val tenorClient: TenorGifClient
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
                UiStringRes(string.no_network_title),
                htmlSubtitle = UiStringRes(string.no_network_message),
                image = drawable.media_picker_lib_load_error_image,
                data = items
            )
        }

        return if (!filter.isNullOrBlank()) {
            suspendCoroutine { cont ->
                tenorClient.search(
                    filter,
                    nextPosition,
                    PAGE_SIZE,
                    onSuccess = { response ->
                        val gifList = response.results.mapNotNull { it.toMediaItem() }

                        items.addAll(gifList)
                        val newPosition = response.next.toIntOrNull() ?: 0
                        val hasMore = newPosition > nextPosition
                        nextPosition = newPosition
                        val result = if (items.isNotEmpty()) {
                            Success(items.toList(), hasMore)
                        } else {
                            Empty(UiStringRes(string.gif_picker_empty_search_list))
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
                                image = ApiR.drawable.media_picker_lib_load_error_image,
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
            ApiR.drawable.media_picker_lib_empty_gallery_image,
            R.drawable.img_tenor_100dp,
            UiStringRes(R.string.gif_powered_by_tenor)
        )
    }

    private fun Result.toMediaItem(): MediaItem? {
        return urlFromCollectionFormat(MediaCollectionFormat.GIF)?.let {
            MediaItem(
                identifier = GifMediaId(
                    MediaUri(it),
                    title
                ),
                url = urlFromCollectionFormat(MediaCollectionFormat.GIF_NANO)!!,
                type = IMAGE,
                dataModified = 0
            )
        }
    }

    private fun Result.urlFromCollectionFormat(format: String) =
        medias.firstOrNull()?.get(format)?.url

    companion object {
        private const val PAGE_SIZE = 36
    }
}
