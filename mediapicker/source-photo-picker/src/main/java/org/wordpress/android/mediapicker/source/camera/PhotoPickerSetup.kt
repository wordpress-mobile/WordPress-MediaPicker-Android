package org.wordpress.android.mediapicker.source.camera

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.PHOTO_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN

class PhotoPickerSetup private constructor() {
    companion object {
        fun build(isMultiSelectAllowed: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = PHOTO_PICKER,
                isMultiSelectEnabled = isMultiSelectAllowed,
                areResultsQueued = false,
                searchMode = HIDDEN
            )
        }
    }
}
