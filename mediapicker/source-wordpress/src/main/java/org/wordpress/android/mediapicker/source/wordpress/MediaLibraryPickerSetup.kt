package org.wordpress.android.mediapicker.source.wordpress

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.WP_MEDIA_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.model.MediaTypes

class MediaLibraryPickerSetup private constructor() {
    companion object {
        fun build(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = WP_MEDIA_LIBRARY,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                needsAccessToStorage = false,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                searchMode = HIDDEN,
                title = R.string.media_library_title
            )
        }
    }
}
