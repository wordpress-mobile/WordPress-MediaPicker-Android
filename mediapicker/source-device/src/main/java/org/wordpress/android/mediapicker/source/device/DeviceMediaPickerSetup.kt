package org.wordpress.android.mediapicker.source.device

import android.os.Build
import android.os.Build.VERSION_CODES
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.source.device.DeviceMediaPickerSetup.MediaTypes.*
import org.wordpress.android.mediapicker.source.device.R.string

class DeviceMediaPickerSetup {
    companion object {
        fun buildMediaPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(SYSTEM_PICKER),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = true,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                isSearchToggledByDefault = false,
                title = getTitle(mediaTypes)
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

        fun buildSystemPicker(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = SYSTEM_PICKER,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = false,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                isSearchToggledByDefault = false,
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
                isSearchToggledByDefault = false,
                title = string.photo_picker_camera_title
            )
        }
    }

    enum class MediaTypes(val allowedTypes: Set<MediaType>) {
        IMAGES(setOf(IMAGE)), VIDEOS(setOf(VIDEO)), IMAGES_AND_VIDEOS(setOf(IMAGE, VIDEO))
    }
}