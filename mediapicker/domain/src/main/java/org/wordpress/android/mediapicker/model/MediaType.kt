package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO

enum class MediaType {
    IMAGE, VIDEO, AUDIO, DOCUMENT
}

enum class MediaTypes(val allowedTypes: Set<MediaType>) {
    IMAGES(setOf(IMAGE)),
    VIDEOS(setOf(VIDEO)),
    AUDIOS(setOf(AUDIO)),
    DOCUMENTS(setOf(DOCUMENT)),
    IMAGES_AND_VIDEOS(setOf(IMAGE, VIDEO)),
    MEDIA(setOf(IMAGE, VIDEO, AUDIO)),
    EVERYTHING(setOf(IMAGE, VIDEO, AUDIO, DOCUMENT));

    companion object {
        fun fromAllowedTypes(allowedTypes: Set<MediaType>) = values().firstOrNull {
            it.allowedTypes == allowedTypes
        } ?: EVERYTHING
    }
}
