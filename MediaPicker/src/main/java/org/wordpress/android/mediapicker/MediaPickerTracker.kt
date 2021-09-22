package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import javax.inject.Inject

class MediaPickerTracker @Inject constructor(
//    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
//    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
//    private val analyticsUtilsWrapper: AnalyticsUtilsWrapper
) {
    suspend fun trackPreview(isVideo: Boolean, identifier: Identifier, mediaPickerSetup: MediaPickerSetup) {
//        withContext(bgDispatcher) {
//            val properties = if (identifier is LocalUri) {
//                analyticsUtilsWrapper.getMediaProperties(
//                    isVideo,
//                    identifier.value,
//                    null
//                )
//            } else {
//                mutableMapOf()
//            }
//            properties.addMediaPickerProperties(mediaPickerSetup)
//            properties["is_video"] = isVideo
//            analyticsTrackerWrapper.track(MEDIA_PICKER_PREVIEW_OPENED, properties)
//        }
    }

    suspend fun trackItemsPicked(identifiers: List<Identifier>, mediaPickerSetup: MediaPickerSetup) {
//        withContext(bgDispatcher) {
//            launch {
//                val isMultiselection = identifiers.size > 1
//                for (identifier in identifiers) {
//                    val isVideo =
//                        org.wordpress.android.util.MediaUtils.isVideo(identifier.toString())
//                    val properties = if (identifier is LocalUri) {
//                        analyticsUtilsWrapper.getMediaProperties(
//                            isVideo,
//                            identifier.value,
//                            null
//                        )
//                    } else {
//                        mutableMapOf()
//                    }
//                    properties["is_part_of_multiselection"] = isMultiselection
//                    if (isMultiselection) {
//                        properties["number_of_media_selected"] = identifiers.size
//                    }
//                    properties.addMediaPickerProperties(mediaPickerSetup)
//                    analyticsTrackerWrapper.track(MEDIA_PICKER_RECENT_MEDIA_SELECTED, properties)
//                }
//            }
//        }
    }

    fun trackIconClick(icon: MediaPickerIcon, mediaPickerSetup: MediaPickerSetup) {
//        when (icon) {
//            is WpStoriesCapture -> analyticsTrackerWrapper.track(
//                    MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE,
//                    mediaPickerSetup.toProperties()
//            )
//            is ChooseFromAndroidDevice -> analyticsTrackerWrapper.track(
//                    MEDIA_PICKER_OPEN_DEVICE_LIBRARY,
//                    mediaPickerSetup.toProperties()
//            )
//            is SwitchSource -> {
//                val event = when (icon.dataSource) {
//                    DEVICE -> MEDIA_PICKER_OPEN_DEVICE_LIBRARY
//                }
//                analyticsTrackerWrapper.track(event, mediaPickerSetup.toProperties())
//            }
//        }
    }

    fun trackSearch(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_SEARCH_TRIGGERED, mediaPickerSetup.toProperties())
    }

    fun trackSearchExpanded(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_SEARCH_EXPANDED, mediaPickerSetup.toProperties())
    }

    fun trackSearchCollapsed(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_SEARCH_COLLAPSED, mediaPickerSetup.toProperties())
    }

    fun trackShowPermissionsScreen(mediaPickerSetup: MediaPickerSetup, isAlwaysDenied: Boolean) {
        val properties = mediaPickerSetup.toProperties()
        properties["always_denied"] = isAlwaysDenied
//        analyticsTrackerWrapper.track(MEDIA_PICKER_SHOW_PERMISSIONS_SCREEN, properties)
    }

    fun trackItemSelected(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_ITEM_SELECTED, mediaPickerSetup.toProperties())
    }

    fun trackItemUnselected(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_ITEM_UNSELECTED, mediaPickerSetup.toProperties())
    }

    fun trackSelectionCleared(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_SELECTION_CLEARED, mediaPickerSetup.toProperties())
    }

    fun trackMediaPickerOpened(mediaPickerSetup: MediaPickerSetup) {
//        analyticsTrackerWrapper.track(MEDIA_PICKER_OPENED, mediaPickerSetup.toProperties())
    }

    private fun MutableMap<String, Any?>.addMediaPickerProperties(
        mediaPickerSetup: MediaPickerSetup
    ): MutableMap<String, Any?> {
//        this["source"] = when (mediaPickerSetup.primaryDataSource) {
//            DEVICE -> "device_media_library"
//        }
//        this["can_multiselect"] = mediaPickerSetup.canMultiselect
//        this["default_search_view"] = mediaPickerSetup.defaultSearchView
        return this
    }

    private fun MediaPickerSetup.toProperties(): MutableMap<String, Any?> {
        return mutableMapOf<String, Any?>().addMediaPickerProperties(this)
    }
}
