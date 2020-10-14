package org.wordpress.android.mediapicker.loader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.wordpress.android.R
import org.wordpress.android.fluxc.store.StockMediaStore
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.mediapicker.MediaItem
import org.wordpress.android.mediapicker.MediaItem.Identifier.StockMediaIdentifier
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Empty
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Failure
import org.wordpress.android.mediapicker.loader.MediaSource.MediaLoadingResult.Success
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringResWithParams
import org.wordpress.android.ui.utils.UiString.UiStringText
import javax.inject.Inject
import javax.inject.Named

class StockMediaDataSource
@Inject constructor(
    private val stockMediaStore: StockMediaStore,
    @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
) : MediaSource {
    override suspend fun load(
        forced: Boolean,
        loadMore: Boolean,
        filter: String?
    ): MediaLoadingResult {
        return withValidFilter(filter) { validFilter ->
            val result = stockMediaStore.fetchStockMedia(validFilter, loadMore)
            val error = result.error
            return@withValidFilter when {
                error != null -> {
                    Failure(error.message)
                }
                else -> {
                    val data = get()
                    if (data.isNotEmpty()) {
                        Success(data, result.canLoadMore)
                    } else {
                        Empty(
                                UiStringRes(R.string.media_empty_search_list),
                                image = R.drawable.img_illustration_empty_results_216dp
                        )
                    }
                }
            }
        } ?: buildDefaultScreen()
    }

    private fun buildDefaultScreen(): MediaLoadingResult {
        val title = UiStringRes(R.string.stock_media_picker_initial_empty_text)
        val link = "<a href='https://pexels.com/'>Pexels</a>"
        val subtitle = UiStringResWithParams(
                R.string.stock_media_picker_initial_empty_subtext,
                listOf(UiStringText(link))
        )
        return Empty(title, subtitle)
    }

    private suspend fun get(): List<MediaItem> {
        return stockMediaStore.getStockMedia()
                .mapNotNull {
                    it.url?.let { url ->
                        MediaItem(
                                StockMediaIdentifier(it.url, it.name, it.title),
                                url,
                                it.name,
                                IMAGE,
                                null,
                                it.date?.toLongOrNull() ?: 0
                        )
                    }
                }
    }

    private suspend fun <T> withValidFilter(filter: String?, action: suspend (filter: String) -> T): T? {
        return if (filter != null && filter.length >= MIN_SEARCH_QUERY_SIZE) {
            withContext(bgDispatcher) {
                action(filter)
            }
        } else {
            null
        }
    }

    companion object {
        private const val MIN_SEARCH_QUERY_SIZE = 3
    }
}
