package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MediaInsertHandler
import org.wordpress.android.mediapicker.api.MediaInsertHandlerFactory
import org.wordpress.android.mediapicker.api.MediaInsertUseCase
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import javax.inject.Inject

class SampleMediaInsertHandlerFactory @Inject constructor(): MediaInsertHandlerFactory {
    override fun build(mediaPickerSetup: MediaPickerSetup): MediaInsertHandler {
        return DefaultMediaInsertUseCase.toMediaInsertHandler()
    }

    private fun MediaInsertUseCase.toMediaInsertHandler() = MediaInsertHandler(this)

    private object DefaultMediaInsertUseCase : MediaInsertUseCase
}
