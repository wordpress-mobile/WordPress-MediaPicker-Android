package org.wordpress.android.mediapicker.insert

import kotlinx.coroutines.flow.flow
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.util.MediaUri
import org.wordpress.android.util.WPMediaUtilsWrapper

class DeviceListInsertUseCase(
    private val wpMediaUtilsWrapper: WPMediaUtilsWrapper,
    private val queueResults: Boolean
) : MediaInsertUseCase {
    override suspend fun insert(identifiers: List<Identifier>) = flow {
        val localUris = identifiers.mapNotNull { it as? LocalUri }
        emit(InsertModel.Progress(actionTitle))
        var failed = false
        val fetchedUris = localUris.mapNotNull { localUri ->
            val fetchedUri = wpMediaUtilsWrapper.fetchMedia(localUri.value)
            if (fetchedUri == null) {
                failed = true
            }
            fetchedUri?.toString()
        }
        if (failed) {
            emit(InsertModel.Error("Failed to fetch local media"))
        } else {
            emit(InsertModel.Success(fetchedUris.map { LocalUri(MediaUri(it), queueResults) }))
        }
    }

    class DeviceListInsertUseCaseFactory(
        private val wpMediaUtilsWrapper: WPMediaUtilsWrapper
    ) {
        fun build(queueResults: Boolean): DeviceListInsertUseCase {
            return DeviceListInsertUseCase(
                    wpMediaUtilsWrapper,
                    queueResults
            )
        }
    }
}
