package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_UNTOGGLED
import org.wordpress.android.mediapicker.model.MediaTypes

class DeviceMediaPickerSetup private constructor() {
    companion object {
        fun buildMediaPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                isMultiSelectEnabled = canMultiSelect,
                areResultsQueued = false,
                searchMode = VISIBLE_UNTOGGLED,
                availableDataSources = setOf(CAMERA, SYSTEM_PICKER),
                allowedTypes = mediaTypes.allowedTypes,
                title = R.string.photo_picker_title
            )
        }

        fun buildSystemPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = SYSTEM_PICKER,
                isMultiSelectEnabled = canMultiSelect,
                areResultsQueued = false,
                searchMode = HIDDEN,
                allowedTypes = mediaTypes.allowedTypes
            )
        }

        fun buildCameraPicker(): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = CAMERA,
                isMultiSelectEnabled = false,
                areResultsQueued = false,
                searchMode = HIDDEN
            )
        }
    }
}
