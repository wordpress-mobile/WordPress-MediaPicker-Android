package org.wordpress.android.mediapicker.api

import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import org.wordpress.android.mediapicker.model.MediaType

data class MediaPickerSetup(
    val primaryDataSource: DataSource,
    val availableDataSources: Set<DataSource>,
    val isMultiSelectEnabled: Boolean,
    val isStoragePermissionRequired: Boolean,
    val allowedTypes: Set<MediaType>,
    val areResultsQueued: Boolean,
    val searchMode: SearchMode,
    @StringRes val title: Int = 0
) {
    enum class DataSource {
        DEVICE, GIF_LIBRARY, CAMERA, SYSTEM_PICKER, WP_MEDIA_LIBRARY
    }

    enum class SearchMode {
        HIDDEN, VISIBLE_TOGGLED, VISIBLE_UNTOGGLED
    }

    fun toBundle(bundle: Bundle) {
        bundle.putInt(KEY_PRIMARY_DATA_SOURCE, primaryDataSource.ordinal)
        bundle.putIntegerArrayList(KEY_AVAILABLE_DATA_SOURCES, ArrayList(availableDataSources.map { it.ordinal }))
        bundle.putIntegerArrayList(KEY_ALLOWED_TYPES, ArrayList(allowedTypes.map { it.ordinal }))
        bundle.putBoolean(KEY_CAN_MULTISELECT, isMultiSelectEnabled)
        bundle.putBoolean(KEY_REQUIRES_STORAGE_PERMISSIONS, isStoragePermissionRequired)
        bundle.putBoolean(KEY_QUEUE_RESULTS, areResultsQueued)
        bundle.putInt(KEY_SEARCH_MODE, searchMode.ordinal)
        bundle.putInt(KEY_TITLE, title)
    }

    fun toIntent(intent: Intent) {
        intent.putExtra(KEY_PRIMARY_DATA_SOURCE, primaryDataSource.ordinal)
        intent.putIntegerArrayListExtra(KEY_AVAILABLE_DATA_SOURCES, ArrayList(availableDataSources.map { it.ordinal }))
        intent.putIntegerArrayListExtra(KEY_ALLOWED_TYPES, ArrayList(allowedTypes.map { it.ordinal }))
        intent.putExtra(KEY_CAN_MULTISELECT, isMultiSelectEnabled)
        intent.putExtra(KEY_REQUIRES_STORAGE_PERMISSIONS, isStoragePermissionRequired)
        intent.putExtra(KEY_QUEUE_RESULTS, areResultsQueued)
        intent.putExtra(KEY_SEARCH_MODE, searchMode.ordinal)
        intent.putExtra(KEY_TITLE, title)
    }

    companion object {
        private const val KEY_PRIMARY_DATA_SOURCE = "key_primary_data_source"
        private const val KEY_AVAILABLE_DATA_SOURCES = "key_available_data_sources"
        private const val KEY_CAN_MULTISELECT = "key_can_multiselect"
        private const val KEY_REQUIRES_STORAGE_PERMISSIONS = "key_requires_storage_permissions"
        private const val KEY_ALLOWED_TYPES = "key_allowed_types"
        private const val KEY_QUEUE_RESULTS = "key_queue_results"
        private const val KEY_SEARCH_MODE = "key_search_mode"
        private const val KEY_TITLE = "key_title"

        fun fromBundle(bundle: Bundle): MediaPickerSetup {
            val dataSource = DataSource.values()[bundle.getInt(KEY_PRIMARY_DATA_SOURCE)]
            val availableDataSources = (bundle.getIntegerArrayList(KEY_AVAILABLE_DATA_SOURCES) ?: listOf<Int>()).map {
                DataSource.values()[it]
            }.toSet()
            val allowedTypes = (bundle.getIntegerArrayList(KEY_ALLOWED_TYPES) ?: listOf<Int>()).map {
                MediaType.values()[it]
            }.toSet()
            val multipleSelectionAllowed = bundle.getBoolean(KEY_CAN_MULTISELECT)
            val requiresStoragePermissions = bundle.getBoolean(KEY_REQUIRES_STORAGE_PERMISSIONS)
            val queueResults = bundle.getBoolean(KEY_QUEUE_RESULTS)
            val searchMode = SearchMode.values()[bundle.getInt(KEY_SEARCH_MODE)]
            val title = bundle.getInt(KEY_TITLE)
            return MediaPickerSetup(
                dataSource,
                availableDataSources,
                multipleSelectionAllowed,
                requiresStoragePermissions,
                allowedTypes,
                queueResults,
                searchMode,
                title
            )
        }

        fun fromIntent(intent: Intent): MediaPickerSetup {
            val dataSource = DataSource.values()[intent.getIntExtra(KEY_PRIMARY_DATA_SOURCE, -1)]
            val availableDataSources = (
                intent.getIntegerArrayListExtra(KEY_AVAILABLE_DATA_SOURCES)
                    ?: listOf<Int>()
                ).map {
                DataSource.values()[it]
            }.toSet()
            val allowedTypes = (intent.getIntegerArrayListExtra(KEY_ALLOWED_TYPES) ?: listOf<Int>()).map {
                MediaType.values()[it]
            }.toSet()
            val multipleSelectionAllowed = intent.getBooleanExtra(KEY_CAN_MULTISELECT, false)
            val requiresStoragePermissions = intent.getBooleanExtra(KEY_REQUIRES_STORAGE_PERMISSIONS, false)
            val queueResults = intent.getBooleanExtra(KEY_QUEUE_RESULTS, false)
            val searchMode = SearchMode.values()[intent.getIntExtra(KEY_SEARCH_MODE, 0)]
            val title = intent.getIntExtra(KEY_TITLE, 0)
            return MediaPickerSetup(
                dataSource,
                availableDataSources,
                multipleSelectionAllowed,
                requiresStoragePermissions,
                allowedTypes,
                queueResults,
                searchMode,
                title
            )
        }
    }

    interface Factory {
        fun build(source: DataSource, isMultiSelectAllowed: Boolean = false): MediaPickerSetup
    }
}
