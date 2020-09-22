package org.wordpress.android.mediapicker

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.ClearFilter
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Filter
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Refresh
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.Start
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult.Failure
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult.NoChange
import org.wordpress.android.mediapicker.MediaSource.MediaLoadingResult.Success
import org.wordpress.android.util.LocaleManagerWrapper

data class MediaLoader(
    private val mediaSource: MediaSource,
    private val localeManagerWrapper: LocaleManagerWrapper
) {
    suspend fun loadMedia(actions: Channel<LoadAction>): Flow<DomainModel> {
        return flow {
            var state = DomainModel()
            for (loadAction in actions) {
                when (loadAction) {
                    is Start -> {
                        if (state.domainItems.isEmpty() || state.error != null) {
                            state = updateState(
                                    buildDomainModel(mediaSource.load(filter = state.filter), state)
                            )
                        }
                    }
                    is Refresh -> {
                        if (loadAction.forced || state.domainItems.isEmpty()) {
                            state = updateState(state.copy(isLoading = true))
                            state = updateState(
                                    buildDomainModel(
                                            mediaSource.load(
                                                    forced = loadAction.forced,
                                                    filter = state.filter
                                            ), state
                                    )
                            )
                        }
                    }
                    is NextPage -> {
                        val load = mediaSource.load(loadMore = true, filter = state.filter)
                        state = updateState(buildDomainModel(load, state))
                    }
                    is Filter -> {
                        if (loadAction.filter != state.filter) {
                            state = updateState(state.copy(filter = loadAction.filter, isLoading = true))
                            val load = mediaSource.load(
                                    filter = state.filter
                            )
                            state = updateState(buildDomainModel(load, state))
                        }
                    }
                    is ClearFilter -> {
                        if (!state.filter.isNullOrEmpty()) {
                            state = updateState(state.copy(filter = null, isLoading = true))
                            val load = mediaSource.load(
                                    filter = state.filter
                            )
                            state = updateState(buildDomainModel(load, state))
                        }
                    }
                }
            }
        }
    }

    private suspend fun FlowCollector<DomainModel>.updateState(
        updatedState: DomainModel
    ): DomainModel {
        emit(updatedState)
        return updatedState
    }

    private fun buildDomainModel(
        partialResult: MediaLoadingResult,
        state: DomainModel
    ): DomainModel {
        return when (partialResult) {
            is Success -> state.copy(
                    isLoading = false,
                    error = null,
                    hasMore = partialResult.hasMore,
                    domainItems = partialResult.data
            )
            is Failure -> state.copy(isLoading = false, error = partialResult.message)
            is NoChange -> state
        }
    }

    sealed class LoadAction {
        data class Start(val filter: String? = null) : LoadAction()
        data class Refresh(val forced: Boolean) : LoadAction()
        data class Filter(val filter: String) : LoadAction()
        object NextPage : LoadAction()
        object ClearFilter : LoadAction()
    }

    data class DomainModel(
        val domainItems: List<MediaItem> = listOf(),
        val error: String? = null,
        val hasMore: Boolean = false,
        val isFilteredResult: Boolean = false,
        val filter: String? = null,
        val isLoading: Boolean = false
    )
}
