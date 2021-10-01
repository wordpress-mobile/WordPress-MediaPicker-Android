package org.wordpress.android.mediapicker.loader

import org.wordpress.android.mediapicker.api.MediaPickerSetup

interface MediaLoaderFactory {
    fun build(siteId: Long, mediaPickerSetup: MediaPickerSetup): MediaLoader
}