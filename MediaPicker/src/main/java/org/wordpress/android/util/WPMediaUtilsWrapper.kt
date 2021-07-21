package org.wordpress.android.util

import android.content.Context
import android.net.Uri

class WPMediaUtilsWrapper(private val context: Context) {
    fun fetchMedia(mediaUri: Uri): Uri? {
        return WPMediaUtils.fetchMedia(context, mediaUri)
    }

    fun fetchMediaToUriWrapper(mediaUri: UriWrapper): UriWrapper? {
        return WPMediaUtils.fetchMedia(context, mediaUri.uri)?.let { UriWrapper(it) }
    }
}
