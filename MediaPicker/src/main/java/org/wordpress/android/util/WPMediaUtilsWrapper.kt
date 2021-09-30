package org.wordpress.android.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.Log
import javax.inject.Inject

class WPMediaUtilsWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    fun fetchMedia(mediaUri: MediaUri): Uri? {
        return WPMediaUtils.fetchMedia(log, context, Uri.parse(mediaUri.uri))
    }
}
