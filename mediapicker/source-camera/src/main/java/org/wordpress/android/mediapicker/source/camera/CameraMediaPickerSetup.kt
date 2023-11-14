package org.wordpress.android.mediapicker.source.camera

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN

object CameraMediaPickerSetup  {
    fun build(): MediaPickerSetup {
        return MediaPickerSetup(
            primaryDataSource = CAMERA,
            isMultiSelectEnabled = false,
            areResultsQueued = false,
            searchMode = HIDDEN
        )
    }
}
