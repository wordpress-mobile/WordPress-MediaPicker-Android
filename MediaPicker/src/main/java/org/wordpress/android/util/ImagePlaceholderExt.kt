package org.wordpress.android.util

import org.wordpress.android.mediapicker.R

fun ImageType.toErrorResource(): Int? {
    return when (this) {
        ImageType.IMAGE -> null // don't display any error drawable
        ImageType.PHOTO -> R.color.placeholder
        ImageType.UNKNOWN -> R.drawable.ic_notice_white_24dp
        ImageType.VIDEO -> R.color.placeholder
        ImageType.ICON -> R.drawable.bg_rectangle_placeholder_radius_2dp
        ImageType.NO_PLACEHOLDER -> null
    }
}

fun ImageType.toPlaceholderResource(): Int? {
    return when (this) {
        ImageType.IMAGE -> null // don't display any placeholder
        ImageType.PHOTO -> R.color.placeholder
        ImageType.UNKNOWN -> R.drawable.legacy_dashicon_format_image_big_grey
        ImageType.VIDEO -> R.color.placeholder
        ImageType.ICON -> R.drawable.bg_rectangle_placeholder_radius_2dp
        ImageType.NO_PLACEHOLDER -> null
    }
}
