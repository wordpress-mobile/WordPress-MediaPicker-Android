package org.wordpress.android.util

import android.content.Context
import android.net.Uri
import org.wordpress.android.mediapicker.util.MediaUri

class WPMediaUtilsWrapper(private val context: Context) {
    fun fetchMedia(mediaUri: MediaUri): Uri? {
        return WPMediaUtils.fetchMedia(context, Uri.parse(mediaUri.s))
    }
}
