package org.wordpress.android.mediapicker.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.wordpress.android.mediapicker.api.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.api.MediaInsertHandler.InsertModel.Success
import org.wordpress.android.mediapicker.model.MediaItem.Identifier

interface MediaInsertUseCase {
    val actionTitle: Int
        get() = R.string.media_uploading_default

    suspend fun insert(identifiers: List<Identifier>): Flow<InsertModel> = flowOf(Success(identifiers))
}
