package org.wordpress.android.mediapicker.source.device

import android.os.Build
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_UNTOGGLED
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaTypes

class DeviceMediaPickerSetup {
    companion object {
        fun buildMediaPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(SYSTEM_PICKER),
                isMultiSelectEnabled = canMultiSelect,
                needsAccessToStorage = true,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                searchMode = VISIBLE_UNTOGGLED,
                title = R.string.photo_picker_title
            )
        }

        fun buildSystemPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = SYSTEM_PICKER,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                needsAccessToStorage = false,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                searchMode = HIDDEN
            )
        }

        // Storage permission isn't required for Android API 29+ because there is a new storage
        // access concept - a scoped storage
        fun buildCameraPicker(): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = CAMERA,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = false,
                needsAccessToStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q,
                allowedTypes = setOf(IMAGE),
                areResultsQueued = false,
                searchMode = HIDDEN
            )
        }
    }
}
