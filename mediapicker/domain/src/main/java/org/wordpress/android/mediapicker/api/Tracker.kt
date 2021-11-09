package org.wordpress.android.mediapicker.api

interface Tracker {
    fun track(event: Event, properties: Map<String, Any?>)

    enum class Event {
        MEDIA_PICKER_PREVIEW_OPENED,
        MEDIA_PICKER_RECENT_MEDIA_SELECTED,
        MEDIA_PICKER_OPEN_GIF_LIBRARY,
        MEDIA_PICKER_OPEN_DEVICE_LIBRARY,
        MEDIA_PICKER_OPEN_SYSTEM_PICKER,
        MEDIA_PICKER_CAPTURE_PHOTO,
        MEDIA_PICKER_SEARCH_TRIGGERED,
        MEDIA_PICKER_SEARCH_EXPANDED,
        MEDIA_PICKER_SEARCH_COLLAPSED,
        MEDIA_PICKER_SHOW_PERMISSIONS_SCREEN,
        MEDIA_PICKER_ITEM_SELECTED,
        MEDIA_PICKER_ITEM_UNSELECTED,
        MEDIA_PICKER_SELECTION_CLEARED,
        MEDIA_PICKER_OPENED,
        MEDIA_PERMISSION_GRANTED,
        MEDIA_PERMISSION_DENIED
    }
}
