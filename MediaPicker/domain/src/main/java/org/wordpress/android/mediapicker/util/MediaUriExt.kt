package org.wordpress.android.mediapicker.util

import android.net.Uri
import org.wordpress.android.mediapicker.model.MediaUri

fun MediaUri.asAndroidUri(): Uri {
    return Uri.parse(this.uri)
}

fun Uri.asMediaUri(): MediaUri {
    return MediaUri(this.toString())
}