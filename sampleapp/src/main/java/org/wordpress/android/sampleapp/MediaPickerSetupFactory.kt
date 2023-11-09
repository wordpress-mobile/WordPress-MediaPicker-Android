package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.model.MediaTypes
import org.wordpress.android.mediapicker.setup.SystemMediaPickerSetup
import org.wordpress.android.mediapicker.source.camera.CameraMediaPickerSetup
import org.wordpress.android.mediapicker.source.device.DeviceMediaPickerSetup
import org.wordpress.android.mediapicker.source.gif.GifMediaPickerSetup
import java.security.InvalidParameterException
import javax.inject.Inject

class MediaPickerSetupFactory @Inject constructor() : MediaPickerSetup.Factory {
    override fun build(source: DataSource, mediaTypes: MediaTypes, isMultiSelectAllowed: Boolean): MediaPickerSetup {
        return when (source) {
            GIF_LIBRARY -> GifMediaPickerSetup.build(canMultiSelect = true)
            CAMERA -> CameraMediaPickerSetup.build()
            DEVICE -> DeviceMediaPickerSetup.build(
                mediaTypes = mediaTypes,
                canMultiSelect = true
            )
            SYSTEM_PICKER -> SystemMediaPickerSetup.build(
                mediaTypes = mediaTypes,
                canMultiSelect = false
            )
            else -> throw InvalidParameterException("${source.name} source is not supported")
        }
    }
}
