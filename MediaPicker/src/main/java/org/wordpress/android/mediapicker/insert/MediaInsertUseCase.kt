package org.wordpress.android.mediapicker.insert

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel.Success
import org.wordpress.android.mediapicker.model.MediaItem.Identifier

interface MediaInsertUseCase {
    val actionTitle: Int
        get() = R.string.media_uploading_default

    suspend fun insert(identifiers: List<Identifier>): Flow<InsertModel> = flowOf(Success(identifiers))
}
