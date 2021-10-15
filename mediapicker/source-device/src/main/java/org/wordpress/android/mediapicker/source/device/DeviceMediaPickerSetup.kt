package org.wordpress.android.mediapicker.source.device

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
                availableDataSources = setOf(CAMERA, SYSTEM_PICKER),
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

    enum class MediaTypes(val allowedTypes: Set<MediaType>) {
        IMAGES(setOf(IMAGE)), VIDEOS(setOf(VIDEO)), IMAGES_AND_VIDEOS(setOf(IMAGE, VIDEO))
    }
}