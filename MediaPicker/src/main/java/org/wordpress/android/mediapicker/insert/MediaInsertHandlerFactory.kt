package org.wordpress.android.mediapicker.insert

import org.wordpress.android.mediapicker.MediaPickerSetup
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.insert.DeviceListInsertUseCase.DeviceListInsertUseCaseFactory

class MediaInsertHandlerFactory(
    private val deviceListInsertUseCaseFactory: DeviceListInsertUseCaseFactory,
) {
    fun build(mediaPickerSetup: MediaPickerSetup): MediaInsertHandler {
        return when (mediaPickerSetup.primaryDataSource) {
            DEVICE -> deviceListInsertUseCaseFactory.build(mediaPickerSetup.queueResults)
        }.toMediaInsertHandler()
    }

    private fun MediaInsertUseCase.toMediaInsertHandler() = MediaInsertHandler(this)

    private object DefaultMediaInsertUseCase : MediaInsertUseCase
}
