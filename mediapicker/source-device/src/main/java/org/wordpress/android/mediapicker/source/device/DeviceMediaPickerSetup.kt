package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_UNTOGGLED
import org.wordpress.android.mediapicker.model.MediaTypes

object DeviceMediaPickerSetup {
    fun build(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
        return MediaPickerSetup(
            primaryDataSource = DEVICE,
            isMultiSelectEnabled = canMultiSelect,
            areResultsQueued = false,
            searchMode = VISIBLE_UNTOGGLED,
            availableDataSources = setOf(SYSTEM_PICKER),
            allowedTypes = mediaTypes.allowedTypes,
            title = R.string.photo_picker_title
        )
    }
}
