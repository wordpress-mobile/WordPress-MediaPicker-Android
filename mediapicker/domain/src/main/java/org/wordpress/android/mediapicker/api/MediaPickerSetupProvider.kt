package org.wordpress.android.mediapicker.api

import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource

interface MediaPickerSetupProvider {
    fun provideSetupForSource(source: DataSource): MediaPickerSetup
}
