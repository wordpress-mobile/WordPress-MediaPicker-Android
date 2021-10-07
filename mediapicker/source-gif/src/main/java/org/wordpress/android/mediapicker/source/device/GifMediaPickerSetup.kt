package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.source.gif.R

class GifMediaPickerSetup {
    companion object {
        fun build(
            canMultiSelect: Boolean
        ): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = GIF_LIBRARY,
                availableDataSources = setOf(),
                canMultiselect = canMultiSelect,
                requiresStoragePermissions = false,
                allowedTypes = setOf(IMAGE),
                allowCameraCapture = false,
                isSystemPickerEnabled = false,
                queueResults = false,
                defaultSearchView = true,
                title = R.string.photo_picker_gif
            )
        }
    }
}