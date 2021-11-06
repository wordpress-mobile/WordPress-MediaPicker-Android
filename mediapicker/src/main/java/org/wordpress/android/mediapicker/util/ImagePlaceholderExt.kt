package org.wordpress.android.mediapicker.util

import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.util.ImageType.ICON
import org.wordpress.android.mediapicker.util.ImageType.IMAGE
import org.wordpress.android.mediapicker.util.ImageType.NO_PLACEHOLDER
import org.wordpress.android.mediapicker.util.ImageType.PHOTO
import org.wordpress.android.mediapicker.util.ImageType.UNKNOWN
import org.wordpress.android.mediapicker.util.ImageType.VIDEO

fun ImageType.toErrorResource(): Int? {
    return when (this) {
        IMAGE -> null // don't display any error drawable
        PHOTO -> R.color.placeholder
        UNKNOWN -> R.drawable.ic_notice_white_24dp
        VIDEO -> R.color.placeholder
        ICON -> R.drawable.bg_rectangle_placeholder_radius_2dp
        NO_PLACEHOLDER -> null
    }
}

fun ImageType.toPlaceholderResource(): Int? {
    return when (this) {
        IMAGE -> null // don't display any placeholder
        PHOTO -> R.color.placeholder
        UNKNOWN -> R.drawable.legacy_dashicon_format_image_big_grey
        VIDEO -> R.color.placeholder
        ICON -> R.drawable.bg_rectangle_placeholder_radius_2dp
        NO_PLACEHOLDER -> null
    }
}
