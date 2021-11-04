package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.api.MediaPickerSetup

sealed class MediaPickerAction {
    data class OpenSystemPicker(
        val pickerContext: MediaPickerContext,
        val mimeTypes: List<String>,
        val allowMultipleSelection: Boolean
    ) : MediaPickerAction()

    object OpenCameraForPhotos : MediaPickerAction()
    data class SwitchMediaPicker(val mediaPickerSetup: MediaPickerSetup) : MediaPickerAction()
}