package org.wordpress.android.mediapicker.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.GIF_MEDIA_ID
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.LOCAL_ID
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.LOCAL_URI
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.REMOTE_ID

@Parcelize
data class MediaItem(
    val identifier: Identifier,
    val url: String,
    val name: String? = null,
    val type: MediaType,
    val mimeType: String? = null,
    val dataModified: Long
) : Parcelable {
    enum class IdentifierType {
        LOCAL_URI,
        REMOTE_ID,
        LOCAL_ID,
        GIF_MEDIA_ID
    }

    sealed class Identifier(val type: IdentifierType) : Parcelable {
        @Parcelize
        data class LocalUri(val uri: MediaUri, val queued: Boolean = false) : Identifier(LOCAL_URI)

        @Parcelize
        data class RemoteId(val value: Long) : Identifier(REMOTE_ID)

        @Parcelize
        data class LocalId(val value: Int) : Identifier(LOCAL_ID)

        @Parcelize
        data class GifMediaId(val uri: MediaUri, val title: String?) : Identifier(GIF_MEDIA_ID)
    }
}
