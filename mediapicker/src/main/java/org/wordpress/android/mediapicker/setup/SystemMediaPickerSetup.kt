package org.wordpress.android.mediapicker.setup

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.model.MediaTypes

object SystemMediaPickerSetup {
    fun build(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
        return MediaPickerSetup(
            primaryDataSource = SYSTEM_PICKER,
            isMultiSelectEnabled = canMultiSelect,
            areResultsQueued = false,
            searchMode = HIDDEN,
            allowedTypes = mediaTypes.allowedTypes
        )
    }
}
