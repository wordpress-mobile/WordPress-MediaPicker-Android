package org.wordpress.android.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaUri
import javax.inject.Inject

class WPMediaUtilsWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun fetchMedia(mediaUri: MediaUri): Uri? {
        return WPMediaUtils.fetchMedia(context, Uri.parse(mediaUri.uri))
    }
}
