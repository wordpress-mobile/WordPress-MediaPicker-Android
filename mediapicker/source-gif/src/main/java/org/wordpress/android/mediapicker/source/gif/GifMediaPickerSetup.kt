package org.wordpress.android.mediapicker.source.gif

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.source.gif.R

class GifMediaPickerSetup {
    companion object {
        fun build(canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = GIF_LIBRARY,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = false,
                allowedTypes = setOf(IMAGE),
                areResultsQueued = false,
                isSearchToggledByDefault = true,
                title = R.string.photo_picker_gif
            )
        }
    }
}
