package org.wordpress.android.mediapicker.api

import org.wordpress.android.mediapicker.api.MediaSource
import org.wordpress.android.mediapicker.model.MediaType

interface MediaSourceFactory {
    fun build(siteId: Long, mediaTypes: Set<MediaType>): MediaSource
}