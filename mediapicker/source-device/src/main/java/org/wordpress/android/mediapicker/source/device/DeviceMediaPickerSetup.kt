package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO

class DeviceMediaPickerSetup {
    companion object {
        fun build(
            isImagePicker: Boolean,
            isVideoPicker: Boolean,
            canMultiSelect: Boolean
        ): MediaPickerSetup {
            val allowedTypes = mutableSetOf<MediaType>()
            if (isImagePicker) {
                allowedTypes.add(IMAGE)
            }
            if (isVideoPicker) {
                allowedTypes.add(VIDEO)
            }
            val title = if (isImagePicker && isVideoPicker) {
                R.string.photo_picker_photo_or_video_title
            } else if (isVideoPicker) {
                R.string.photo_picker_video_title
            } else {
                R.string.photo_picker_title
            }
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(),
                canMultiselect = canMultiSelect,
                requiresStoragePermissions = true,
                allowedTypes = allowedTypes,
                allowCameraCapture = true,
                isSystemPickerEnabled = true,
                queueResults = false,
                defaultSearchView = false,
                title = title
            )
        }
    }
}