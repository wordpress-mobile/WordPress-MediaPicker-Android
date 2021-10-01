package org.wordpress.android.mediapicker.source.device

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.source.device.DeviceMediaSource.DeviceMediaSourceFactory
import javax.inject.Inject

class MediaLoaderFactory @Inject constructor(
    private val deviceMediaSourceFactory: DeviceMediaSourceFactory,
) {
    fun build(mediaPickerSetup: MediaPickerSetup, siteId: Long): MediaLoader {
        return when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> deviceMediaSourceFactory.build(siteId, mediaPickerSetup.allowedTypes)
        }.toMediaLoader()
    }
}