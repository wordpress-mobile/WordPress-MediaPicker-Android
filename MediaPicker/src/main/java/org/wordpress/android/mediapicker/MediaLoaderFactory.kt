package org.wordpress.android.mediapicker

import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.mediapicker.DeviceListBuilder.DeviceListBuilderFactory
import org.wordpress.android.mediapicker.MediaLibraryDataSource.MediaLibraryDataSourceFactory
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.STOCK_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.WP_LIBRARY
import org.wordpress.android.mediapicker.StockMediaDataSource.StockMediaDataSourceFactory
import org.wordpress.android.util.LocaleManagerWrapper
import javax.inject.Inject

class MediaLoaderFactory
@Inject constructor(
    private val deviceListBuilderFactory: DeviceListBuilderFactory,
    private val mediaLibraryDataSourceFactory: MediaLibraryDataSourceFactory,
    private val stockMediaDataSourceFactory: StockMediaDataSourceFactory,
    private val localeManagerWrapper: LocaleManagerWrapper
) {
    fun build(mediaPickerSetup: MediaPickerSetup, siteModel: SiteModel?): MediaLoader {
        return when (mediaPickerSetup.dataSource) {
            DEVICE -> deviceListBuilderFactory.build(mediaPickerSetup.allowedTypes, mediaPickerSetup.queueResults)
            WP_LIBRARY -> mediaLibraryDataSourceFactory.build(requireNotNull(siteModel) {
                "Site is necessary when loading WP media library "
            }, mediaPickerSetup.allowedTypes)
            STOCK_LIBRARY -> stockMediaDataSourceFactory.build(requireNotNull(siteModel))
            GIF_LIBRARY -> throw NotImplementedError("Source not implemented yet")
        }.toMediaLoader()
    }

    private fun MediaSource.toMediaLoader() = MediaLoader(this, localeManagerWrapper)
}
