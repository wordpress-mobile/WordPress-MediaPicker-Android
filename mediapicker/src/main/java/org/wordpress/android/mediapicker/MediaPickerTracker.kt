package org.wordpress.android.mediapicker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.*
import org.wordpress.android.mediapicker.util.Tracker
import org.wordpress.android.mediapicker.util.Tracker.Event.*
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested
import org.wordpress.android.util.MediaUtils
import javax.inject.Inject

class MediaPickerTracker @Inject constructor(
    private val bgDispatcher: CoroutineDispatcher,
    private val tracker: Tracker
) {
    suspend fun trackPreview(isVideo: Boolean, identifier: Identifier, mediaPickerSetup: MediaPickerSetup) {
        withContext(bgDispatcher) {
            val properties: MutableMap<String, Any?> = getMediaProperties(identifier)
            properties.addMediaPickerProperties(mediaPickerSetup)
            properties["is_video"] = isVideo
            tracker.track(MEDIA_PICKER_PREVIEW_OPENED, properties)
        }
    }

    suspend fun trackItemsPicked(identifiers: List<Identifier>, mediaPickerSetup: MediaPickerSetup) {
        withContext(bgDispatcher) {
            launch {
                val isMultiSelection = identifiers.size > 1
                for (identifier in identifiers) {
                    val properties: MutableMap<String, Any?> = getMediaProperties(identifier)
                    properties["is_part_of_multiselection"] = isMultiSelection
                    if (isMultiSelection) {
                        properties["number_of_media_selected"] = identifiers.size
                    }
                    properties.addMediaPickerProperties(mediaPickerSetup)
                    tracker.track(MEDIA_PICKER_RECENT_MEDIA_SELECTED, properties)
                }
            }
        }
    }

    private fun getMediaProperties(identifier: Identifier): MutableMap<String, Any?> {
        val properties: MutableMap<String, Any?> = if (identifier is LocalUri) {
            val isVideo = MediaUtils.isVideo(identifier.toString())
            mutableMapOf("is_video" to isVideo, "uri" to identifier.uri)
        } else {
            mutableMapOf()
        }
        return properties
    }

    fun trackIconClick(action: MediaPickerActionEvent, mediaPickerSetup: MediaPickerSetup) {
        when (action) {
            is ChooseFromAndroidDevice -> tracker.track(
                MEDIA_PICKER_OPEN_SYSTEM_PICKER,
                mediaPickerSetup.toProperties()
            )
            is SwitchSource -> {
                val event = when (action.dataSource) {
                    DEVICE -> MEDIA_PICKER_OPEN_DEVICE_LIBRARY
                    GIF_LIBRARY -> MEDIA_PICKER_OPEN_GIF_LIBRARY
                    CAMERA -> MEDIA_PICKER_CAPTURE_PHOTO
                    SYSTEM_PICKER -> MEDIA_PICKER_OPEN_SYSTEM_PICKER
                }
                tracker.track(event, mediaPickerSetup.toProperties())
            }
            is CapturePhoto -> {
                tracker.track(
                    MEDIA_PICKER_CAPTURE_PHOTO,
                    mediaPickerSetup.toProperties()
                )
            }
        }
    }

    fun trackSearch(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_SEARCH_TRIGGERED, mediaPickerSetup.toProperties())
    }

    fun trackSearchExpanded(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_SEARCH_EXPANDED, mediaPickerSetup.toProperties())
    }

    fun trackSearchCollapsed(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_SEARCH_COLLAPSED, mediaPickerSetup.toProperties())
    }

    fun trackShowPermissionsScreen(
        mediaPickerSetup: MediaPickerSetup,
        permission: PermissionsRequested,
        isAlwaysDenied: Boolean
    ) {
        val properties = mediaPickerSetup.toProperties()
        properties["always_denied"] = isAlwaysDenied
        properties["permission_requested"] = permission.name
        tracker.track(MEDIA_PICKER_SHOW_PERMISSIONS_SCREEN, properties)
    }

    fun trackItemSelected(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_ITEM_SELECTED, mediaPickerSetup.toProperties())
    }

    fun trackItemUnselected(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_ITEM_UNSELECTED, mediaPickerSetup.toProperties())
    }

    fun trackSelectionCleared(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_SELECTION_CLEARED, mediaPickerSetup.toProperties())
    }

    fun trackMediaPickerOpened(mediaPickerSetup: MediaPickerSetup) {
        tracker.track(MEDIA_PICKER_OPENED, mediaPickerSetup.toProperties())
    }

    private fun MutableMap<String, Any?>.addMediaPickerProperties(
        mediaPickerSetup: MediaPickerSetup
    ): MutableMap<String, Any?> {
        this["source"] = when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> "device_media_library"
            GIF_LIBRARY -> "gif_library"
            SYSTEM_PICKER -> "system_picker"
            CAMERA -> "camera"
        }
        this["can_multiselect"] = mediaPickerSetup.isMultiSelectEnabled
        this["default_search_view"] = mediaPickerSetup.isSearchToggledByDefault
        return this
    }

    private fun MediaPickerSetup.toProperties(): MutableMap<String, Any?> {
        return mutableMapOf<String, Any?>().addMediaPickerProperties(this)
    }
}
