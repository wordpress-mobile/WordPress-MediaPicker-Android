package org.wordpress.android.mediapicker.model

import android.content.Intent
import org.wordpress.android.mediapicker.api.R

enum class MediaPickerContext(
    val intentAction: String,
    val title: Int,
    val mediaTypeFilter: String
) {
    PHOTO(
        Intent.ACTION_GET_CONTENT,
        R.string.pick_photo, "image/*"
    ),
    VIDEO(
        Intent.ACTION_GET_CONTENT,
        R.string.pick_video, "video/*"
    ),
    PHOTO_OR_VIDEO(
        Intent.ACTION_GET_CONTENT,
        R.string.pick_media, "*/*"
    ),
    AUDIO(
        Intent.ACTION_GET_CONTENT,
        R.string.pick_audio, "*/*"
    ),
    MEDIA_FILE(
        Intent.ACTION_GET_CONTENT,
        R.string.pick_audio, "*/*"
    ),
    FILE(
        Intent.ACTION_OPEN_DOCUMENT,
        R.string.pick_file, "*/*"
    );
}
