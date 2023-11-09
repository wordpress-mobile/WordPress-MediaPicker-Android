package org.wordpress.android.mediapicker.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.GIF_MEDIA
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.LOCAL
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.LOCAL_URI
import org.wordpress.android.mediapicker.model.MediaItem.IdentifierType.REMOTE_MEDIA

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
        REMOTE_MEDIA,
        LOCAL,
        GIF_MEDIA
    }

    sealed class Identifier(val type: IdentifierType) : Parcelable {
        @Parcelize
        data class LocalUri(val uri: MediaUri, val queued: Boolean = false) : Identifier(LOCAL_URI)

        @Parcelize
        data class RemoteMedia(
            val id: Long,
            val name: String?,
            val url: String,
            val date: String?
        ) : Identifier(REMOTE_MEDIA)

        @Parcelize
        data class LocalMedia(val id: Int) : Identifier(LOCAL)

        @Parcelize
        data class GifMedia(val uri: MediaUri, val title: String?) : Identifier(GIF_MEDIA)
    }
}
