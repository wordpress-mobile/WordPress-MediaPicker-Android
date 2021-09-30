package org.wordpress.android.mediapicker.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaUri
import javax.inject.Inject

class MediaUtilsWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    fun fetchMedia(mediaUri: MediaUri): Uri? {
        return MediaUtils.fetchMedia(log, context, Uri.parse(mediaUri.uri))
    }
}
