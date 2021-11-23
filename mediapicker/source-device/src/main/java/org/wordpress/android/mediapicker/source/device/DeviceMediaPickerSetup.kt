package org.wordpress.android.mediapicker.source.device

import android.os.Build
import android.os.Build.VERSION_CODES
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_UNTOGGLED
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaTypes
import org.wordpress.android.mediapicker.model.MediaTypes.IMAGES
import org.wordpress.android.mediapicker.model.MediaTypes.IMAGES_AND_VIDEOS
import org.wordpress.android.mediapicker.model.MediaTypes.VIDEOS
import org.wordpress.android.mediapicker.source.device.R.string

class DeviceMediaPickerSetup {
    companion object {
        fun buildMediaPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(SYSTEM_PICKER, CAMERA, GIF_LIBRARY),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = true,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                searchMode = VISIBLE_UNTOGGLED,
                title = getTitle(mediaTypes)
            )
        }

        fun buildSystemPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = SYSTEM_PICKER,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = false,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                searchMode = HIDDEN,
                title = getTitle(mediaTypes)
            )
        }

        // Storage permission isn't required for Android API 29+ because there is a new storage
        // access concept - a scoped storage
        fun buildCameraPicker(): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = CAMERA,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = false,
                isStoragePermissionRequired = Build.VERSION.SDK_INT < VERSION_CODES.Q,
                allowedTypes = setOf(IMAGE),
                areResultsQueued = false,
                searchMode = HIDDEN,
                title = string.photo_picker_camera_title
            )
        }

        private fun getTitle(mediaTypes: MediaTypes): Int {
            val title = when (mediaTypes) {
                IMAGES_AND_VIDEOS -> string.photo_picker_photo_or_video_title
                VIDEOS -> string.photo_picker_video_title
                IMAGES -> string.photo_picker_title
            }
            return title
        }
    }
}
