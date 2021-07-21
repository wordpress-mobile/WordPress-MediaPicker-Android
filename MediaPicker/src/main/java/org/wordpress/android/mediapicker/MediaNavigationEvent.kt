package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.util.UriWrapper

sealed class MediaNavigationEvent {
    data class PreviewUrl(val url: String) : MediaNavigationEvent()
    data class PreviewMedia(val mediaId: Long) : MediaNavigationEvent()
    data class EditMedia(val uris: List<UriWrapper>) : MediaNavigationEvent()
    data class InsertMedia(val identifiers: List<Identifier>) : MediaNavigationEvent()
    data class IconClickEvent(val action: MediaPickerAction) : MediaNavigationEvent()
    object Exit : MediaNavigationEvent()
}
