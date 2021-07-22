package org.wordpress.android.mediapicker.loader

import org.wordpress.android.mediapicker.MediaPickerSetup
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.loader.DeviceListBuilder.DeviceListBuilderFactory

class MediaLoaderFactory(
    private val deviceListBuilderFactory: DeviceListBuilderFactory,
//    private val mediaLibraryDataSourceFactory: MediaLibraryDataSourceFactory,
//    private val stockMediaDataSource: StockMediaDataSource,
//    private val gifMediaDataSource: GifMediaDataSource,
) {
    fun build(mediaPickerSetup: MediaPickerSetup, siteId: Long): MediaLoader {
        return when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> deviceListBuilderFactory.build(siteId, mediaPickerSetup.allowedTypes)
//            WP_LIBRARY -> mediaLibraryDataSourceFactory.build(requireNotNull(siteModel) {
//                "Site is necessary when loading WP media library "
//            }, mediaPickerSetup.allowedTypes)
//            STOCK_LIBRARY -> stockMediaDataSource
//            GIF_LIBRARY -> gifMediaDataSource
        }.toMediaLoader()
    }

    private fun MediaSource.toMediaLoader() = MediaLoader(this)
}
