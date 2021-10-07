package org.wordpress.android.mediapicker.source.device.util

import android.net.Uri
import dagger.Reusable
import org.wordpress.android.mediapicker.model.MediaUri
import javax.inject.Inject

@Reusable
class UriUtilsWrapper @Inject constructor() {
    fun parse(uriString: String?): MediaUri = MediaUri(Uri.parse(uriString).toString())
}
