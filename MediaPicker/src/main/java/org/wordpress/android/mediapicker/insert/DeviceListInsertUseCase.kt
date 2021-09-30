package org.wordpress.android.mediapicker.insert

import kotlinx.coroutines.flow.flow
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.MediaUtilsWrapper
import javax.inject.Inject

class DeviceListInsertUseCase constructor(
    private val mediaUtilsWrapper: MediaUtilsWrapper,
    private val queueResults: Boolean
) : MediaInsertUseCase {
    override suspend fun insert(identifiers: List<Identifier>) = flow {
        val localUris = identifiers.mapNotNull { it as? LocalUri }
        emit(InsertModel.Progress(actionTitle))
        var failed = false
        val fetchedUris = localUris.mapNotNull { localUri ->
            val fetchedUri = mediaUtilsWrapper.fetchMedia(localUri.value)
            if (fetchedUri == null) {
                failed = true
            }
            fetchedUri?.toString()
        }
        if (failed) {
            emit(InsertModel.Error("Failed to fetch local media"))
        } else {
            emit(InsertModel.Success(fetchedUris.map { LocalUri(
                MediaUri(
                    it
                ), queueResults) }))
        }
    }

    class DeviceListInsertUseCaseFactory @Inject constructor(
        private val mediaUtilsWrapper: MediaUtilsWrapper
    ) {
        fun build(queueResults: Boolean): DeviceListInsertUseCase {
            return DeviceListInsertUseCase(
                    mediaUtilsWrapper,
                    queueResults
            )
        }
    }
}
