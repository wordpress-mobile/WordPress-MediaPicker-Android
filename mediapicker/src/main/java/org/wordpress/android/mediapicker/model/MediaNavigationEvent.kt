package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.mediapicker.model.MediaItem.Identifier

sealed class MediaNavigationEvent {
    data class PreviewUrl(val url: String) : MediaNavigationEvent()
    data class PreviewMedia(val mediaId: Long) : MediaNavigationEvent()
    data class InsertMedia(val identifiers: List<Identifier>) : MediaNavigationEvent()
    data class IconClickEvent(val action: MediaPickerAction) : MediaNavigationEvent()
    object Exit : MediaNavigationEvent()
    object ShowAppSettings : MediaNavigationEvent()
    object RequestStoragePermission : MediaNavigationEvent()
    object RequestCameraPermission : MediaNavigationEvent()
}
