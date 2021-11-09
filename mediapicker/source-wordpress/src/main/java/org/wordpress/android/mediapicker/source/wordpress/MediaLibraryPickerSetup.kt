package org.wordpress.android.mediapicker.source.wordpress

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.WP_MEDIA_LIBRARY
import org.wordpress.android.mediapicker.model.MediaTypes

class MediaLibraryPickerSetup {
    companion object {
        fun build(mediaTypes: MediaTypes, canMultiSelect: Boolean): MediaPickerSetup {
            return MediaPickerSetup(
                primaryDataSource = WP_MEDIA_LIBRARY,
                availableDataSources = emptySet(),
                isMultiSelectEnabled = canMultiSelect,
                isStoragePermissionRequired = false,
                allowedTypes = mediaTypes.allowedTypes,
                areResultsQueued = false,
                isSearchToggledByDefault = false,
                title = R.string.media_library_title
            )
        }
    }
}
