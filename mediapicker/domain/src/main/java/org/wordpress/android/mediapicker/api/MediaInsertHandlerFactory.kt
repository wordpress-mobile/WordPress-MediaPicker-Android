package org.wordpress.android.mediapicker.api

interface MediaInsertHandlerFactory {
    fun build(mediaPickerSetup: MediaPickerSetup): MediaInsertHandler
}