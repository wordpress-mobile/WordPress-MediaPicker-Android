package org.wordpress.android.util

import android.net.Uri
import org.wordpress.android.mediapicker.util.MediaUri

fun MediaUri.asAndroidUri(): Uri {
    return Uri.parse(this.s)
}

fun Uri.asMediaUri(): MediaUri{
    return MediaUri(this.toString())
}