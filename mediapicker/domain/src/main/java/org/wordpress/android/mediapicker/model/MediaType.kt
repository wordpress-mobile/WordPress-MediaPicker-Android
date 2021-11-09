package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO

enum class MediaType {
    IMAGE, VIDEO, AUDIO, DOCUMENT
}

enum class MediaTypes(val allowedTypes: Set<MediaType>) {
    IMAGES(setOf(IMAGE)), VIDEOS(setOf(VIDEO)), IMAGES_AND_VIDEOS(setOf(IMAGE, VIDEO))
}