package org.wordpress.android.mediapicker.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.util.MediaUtils
import java.lang.IllegalStateException
import javax.inject.Inject

class MediaFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    fun fetchMedia(mediaUri: MediaUri): Uri? {
        return fetchMedia(log, context, Uri.parse(mediaUri.uri))
    }

    private fun fetchMedia(log: Log, context: Context, mediaUri: Uri): Uri? {
        if (MediaUtils.isInMediaStore(mediaUri)) {
            return mediaUri
        }

        return try {
            // Do not download the file in async task. See
            // https://github.com/wordpress-mobile/WordPress-Android/issues/5818
            MediaUtils.downloadExternalMedia(context, mediaUri)
        } catch (e: IllegalStateException) {
            // Ref: https://github.com/wordpress-mobile/WordPress-Android/issues/5823
            log.e(
                "Can't download the image at: " + mediaUri.toString() +
                    " See issue #5823",
                e
            )
            null
        }
    }
}
