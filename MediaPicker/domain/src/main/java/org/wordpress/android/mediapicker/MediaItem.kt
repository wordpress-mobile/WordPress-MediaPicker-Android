package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaItem.IdentifierType.LOCAL_ID
import org.wordpress.android.mediapicker.MediaItem.IdentifierType.LOCAL_URI
import org.wordpress.android.mediapicker.MediaItem.IdentifierType.REMOTE_ID
import org.wordpress.android.mediapicker.util.Uri

data class MediaItem(
    val identifier: Identifier,
    val url: String,
    val name: String? = null,
    val type: MediaType,
    val mimeType: String? = null,
    val dataModified: Long
) {
    enum class IdentifierType {
        LOCAL_URI,
        REMOTE_ID,
        LOCAL_ID,
    }

    sealed class Identifier(val type: IdentifierType) {
        data class LocalUri(val value: Uri, val queued: Boolean = false) : Identifier(LOCAL_URI)

        data class RemoteId(val value: Long) : Identifier(REMOTE_ID)

        data class LocalId(val value: Int) : Identifier(LOCAL_ID)
    }
}
