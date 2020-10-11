package org.wordpress.android.mediapicker.insert

import org.wordpress.android.R
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.insert.MediaInsertUseCase.MediaInsertResult.Success

interface MediaInsertUseCase {
    val actionTitle: Int
            get() = R.string.media_uploading_default
    suspend fun insert(identifiers: List<Identifier>): MediaInsertResult = Success(identifiers)

    sealed class MediaInsertResult {
        data class Success(val identifiers: List<Identifier>) : MediaInsertResult()
        data class Failure(val message: String) : MediaInsertResult()
    }
}
