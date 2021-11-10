package org.wordpress.android.mediapicker.model

import android.net.Uri
import org.wordpress.android.mediapicker.model.MediaItem.Identifier

internal sealed class MediaNavigationEvent {
    data class PreviewUrl(val url: String) : MediaNavigationEvent()
    data class PreviewMedia(val mediaId: Long) : MediaNavigationEvent()
    data class ReturnSelectedMedia(val identifiers: List<Identifier>) : MediaNavigationEvent()
    data class ReturnCapturedImage(
        val areResultsQueued: Boolean,
        val capturedImageUri: Uri
    ) : MediaNavigationEvent()
    data class ChooseMediaPickerAction(val action: MediaPickerAction) : MediaNavigationEvent()
    object Exit : MediaNavigationEvent()
    object ShowAppSettings : MediaNavigationEvent()
    object RequestStoragePermission : MediaNavigationEvent()
    object RequestCameraPermission : MediaNavigationEvent()
}
