package org.wordpress.android.mediapicker.insert

//import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.GIF_LIBRARY
//import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.STOCK_LIBRARY
//import org.wordpress.android.mediapicker.insert.GifMediaInsertUseCase.GifMediaInsertUseCaseFactory
//import org.wordpress.android.mediapicker.insert.StockMediaInsertUseCase.StockMediaInsertUseCaseFactory
import org.wordpress.android.mediapicker.MediaPickerSetup
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.insert.DeviceListInsertUseCase.DeviceListInsertUseCaseFactory

class MediaInsertHandlerFactory(
    private val deviceListInsertUseCaseFactory: DeviceListInsertUseCaseFactory,
//    private val stockMediaInsertUseCaseFactory: StockMediaInsertUseCaseFactory,
//    private val gifMediaInsertUseCaseFactory: GifMediaInsertUseCaseFactory
) {
    fun build(mediaPickerSetup: MediaPickerSetup): MediaInsertHandler {
        return when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> deviceListInsertUseCaseFactory.build(mediaPickerSetup.queueResults)
//            WP_LIBRARY -> DefaultMediaInsertUseCase
//            STOCK_LIBRARY -> stockMediaInsertUseCaseFactory.build(requireNotNull(siteModel) {
//                "Site is necessary when inserting into stock media library "
//            })
//            GIF_LIBRARY -> gifMediaInsertUseCaseFactory.build(requireNotNull(siteModel) {
//                "Site is necessary when inserting into gif media library "
//            })
        }.toMediaInsertHandler()
    }

    private fun MediaInsertUseCase.toMediaInsertHandler() = MediaInsertHandler(this)

    private object DefaultMediaInsertUseCase : MediaInsertUseCase
}
