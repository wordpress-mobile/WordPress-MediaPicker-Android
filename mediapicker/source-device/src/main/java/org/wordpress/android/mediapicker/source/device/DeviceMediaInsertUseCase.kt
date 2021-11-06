package org.wordpress.android.mediapicker.source.device

import kotlinx.coroutines.flow.flow
import org.wordpress.android.mediapicker.api.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.api.MediaInsertUseCase
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.MediaFetcher
import javax.inject.Inject

class DeviceMediaInsertUseCase constructor(
    private val mediaFetcher: MediaFetcher,
    private val queueResults: Boolean
) : MediaInsertUseCase {
    override suspend fun insert(identifiers: List<Identifier>) = flow {
        val localUris = identifiers.mapNotNull { it as? LocalUri }
        emit(InsertModel.Progress(actionTitle))
        var failed = false
        val fetchedUris = localUris.mapNotNull { localUri ->
            val fetchedUri = mediaFetcher.fetchMedia(localUri.uri)
            if (fetchedUri == null) {
                failed = true
            }
            fetchedUri?.toString()
        }
        if (failed) {
            emit(InsertModel.Error("Failed to fetch local media"))
        } else {
            emit(
                InsertModel.Success(
                    fetchedUris.map {
                        LocalUri(
                            MediaUri(
                                it
                            ),
                            queueResults
                        )
                    }
                )
            )
        }
    }

    class DeviceMediaInsertUseCaseFactory @Inject constructor(
        private val mediaFetcher: MediaFetcher
    ) {
        fun build(queueResults: Boolean): DeviceMediaInsertUseCase {
            return DeviceMediaInsertUseCase(
                mediaFetcher,
                queueResults
            )
        }
    }
}
