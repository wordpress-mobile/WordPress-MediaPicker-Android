package org.wordpress.android.mediapicker.source.gif

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_TOGGLED
import org.wordpress.android.mediapicker.model.MediaType.IMAGE

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
                searchMode = VISIBLE_TOGGLED,
                title = R.string.photo_picker_gif
            )
        }
    }
}
