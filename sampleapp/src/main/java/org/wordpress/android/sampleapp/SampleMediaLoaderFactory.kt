package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.source.device.DeviceMediaSource
import org.wordpress.android.mediapicker.source.gif.GifMediaDataSource
import javax.inject.Inject

class SampleMediaLoaderFactory @Inject constructor(
    private val deviceMediaSourceFactory: DeviceMediaSource.Factory,
    private val gifMediaDataSource: GifMediaDataSource
) : MediaLoaderFactory {
    override fun build(mediaPickerSetup: MediaPickerSetup): MediaLoader {
        return when (mediaPickerSetup.primaryDataSource) {
            GIF_LIBRARY -> gifMediaDataSource
            DEVICE -> deviceMediaSourceFactory.build(mediaPickerSetup.allowedTypes)
            else -> throw UnsupportedOperationException(
                "Unsupported data source: ${mediaPickerSetup.primaryDataSource}"
            )
        }.toMediaLoader()
    }
}
