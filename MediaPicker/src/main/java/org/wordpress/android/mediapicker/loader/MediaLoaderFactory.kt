package org.wordpress.android.mediapicker.loader

import org.wordpress.android.mediapicker.MediaPickerSetup
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.loader.DeviceListBuilder.DeviceListBuilderFactory
import javax.inject.Inject

class MediaLoaderFactory @Inject constructor(
    private val deviceListBuilderFactory: DeviceListBuilderFactory,
) {
    fun build(mediaPickerSetup: MediaPickerSetup, siteId: Long): MediaLoader {
        return when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> deviceListBuilderFactory.build(siteId, mediaPickerSetup.allowedTypes)
        }.toMediaLoader()
    }

    private fun MediaSource.toMediaLoader() = MediaLoader(this)
}
