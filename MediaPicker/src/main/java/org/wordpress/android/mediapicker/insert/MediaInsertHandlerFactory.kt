package org.wordpress.android.mediapicker.insert

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.insert.DeviceListInsertUseCase.DeviceListInsertUseCaseFactory
import javax.inject.Inject

class MediaInsertHandlerFactory @Inject constructor(
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
