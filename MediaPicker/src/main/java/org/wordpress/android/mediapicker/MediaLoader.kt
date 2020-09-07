package org.wordpress.android.mediapicker

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.ClearFilter
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Filter
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Refresh
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Start
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult.Failure
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult.Success
import org.wordpress.android.util.LocaleManagerWrapper

data class MediaLoader(private val mediaSource: MediaSource, private val localeManagerWrapper: LocaleManagerWrapper) {
    suspend fun loadMedia(actions: Channel<LoadAction>): Flow<DomainModel> {
        return flow {
            var state = DomainState()
            for (loadAction in actions) {
                when (loadAction) {
                    is Start -> {
                        if (state.mediaTypes != loadAction.mediaTypes || state.items.isEmpty() || state.error != null) {
                            state = refreshData(
                                    state,
                                    loadAction.mediaTypes,
                                    state.items.isEmpty()
                            ).copy(filter = loadAction.filter)
                        }
                    }
                    is Refresh -> {
                        state.mediaTypes?.let { mediaTypes ->
                            state = refreshData(state, mediaTypes, forced = true)
                        }
                    }
                    is NextPage -> {
                        state.mediaTypes?.let { mediaTypes ->
                            state = when (val mediaLoadingResult = mediaSource.load(
                                    mediaTypes,
                                    loadMore = true
                            )) {
                                is Success -> {
                                    state.copy(
                                            items = state.items + mediaLoadingResult.mediaItems,
                                            hasMore = mediaLoadingResult.hasMore,
                                            error = null
                                    )
                                }
                                is Failure -> {
                                    state.copy(
                                            error = mediaLoadingResult.message
                                    )
                                }
                            }
                        }
                    }
                    is Filter -> {
                        state = state.copy(filter = loadAction.filter)
                    }
                    is ClearFilter -> {
                        state = state.copy(filter = null)
                    }
                }
                if (state.isNotInitialState()) {
                    emit(buildDomainModel(state))
                }
            }
        }
    }

    private suspend fun refreshData(state: DomainState, mediaTypes: Set<MediaType>, forced: Boolean): DomainState {
        return when (val mediaLoadingResult = mediaSource.load(mediaTypes)) {
            is Success -> {
                state.copy(
                        items = mediaLoadingResult.mediaItems,
                        hasMore = mediaLoadingResult.hasMore,
                        mediaTypes = mediaTypes,
                        error = null
                )
            }
            is Failure -> {
                state.copy(
                        error = mediaLoadingResult.message,
                        mediaTypes = mediaTypes,
                        hasMore = false
                )
            }
        }
    }

    private fun buildDomainModel(state: DomainState): DomainModel {
        return if (!state.filter.isNullOrEmpty()) {
            val filter = state.filter.toLowerCase(localeManagerWrapper.getLocale())
            DomainModel(
                    state.items.filter {
                        it.name?.toLowerCase(localeManagerWrapper.getLocale())
                                ?.contains(filter) == true
                    },
                    state.error,
                    state.hasMore,
                    isFilteredResult = true,
                    filter = state.filter
            )
        } else {
            DomainModel(state.items, state.error, state.hasMore, isFilteredResult = false, filter = state.filter)
        }
    }

    sealed class LoadAction {
        data class Start(val mediaTypes: Set<MediaType>, val filter: String? = null) : LoadAction()
        object Refresh : LoadAction()
        data class Filter(val filter: String) : LoadAction()
        object NextPage : LoadAction()
        object ClearFilter : LoadAction()
    }

    data class DomainModel(
        val domainItems: List<MediaItem>,
        val error: String? = null,
        val hasMore: Boolean = false,
        val isFilteredResult: Boolean = false,
        val filter: String? = null
    )

    private data class DomainState(
        val mediaTypes: Set<MediaType>? = null,
        val items: List<MediaItem> = listOf(),
        val hasMore: Boolean = false,
        val filter: String? = null,
        val error: String? = null
    ) {
        fun isNotInitialState(): Boolean = mediaTypes != null
    }
}
