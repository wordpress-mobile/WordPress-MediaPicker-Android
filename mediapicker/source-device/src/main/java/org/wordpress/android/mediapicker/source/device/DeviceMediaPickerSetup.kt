package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO

class DeviceMediaPickerSetup {
    companion object {
        fun buildFilePicker(
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
                availableDataSources = setOf(CAMERA, SYSTEM_PICKER),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = true,
                allowedTypes = allowedTypes,
                areResultsQueued = false,
                isSearchToggledByDefault = false,
                title = title
            )
        }

        fun buildCameraPicker(): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = CAMERA,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = false,
                isStoragePermissionRequired = true,
                allowedTypes = setOf(IMAGE),
                areResultsQueued = false,
                isSearchToggledByDefault = false,
                title = 0
            )
        }
    }
}