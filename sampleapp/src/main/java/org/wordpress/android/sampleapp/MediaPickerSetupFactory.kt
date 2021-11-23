package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.model.MediaTypes.IMAGES
import org.wordpress.android.mediapicker.source.device.DeviceMediaPickerSetup
import org.wordpress.android.mediapicker.source.gif.GifMediaPickerSetup
import java.security.InvalidParameterException
import javax.inject.Inject

class MediaPickerSetupFactory @Inject constructor() : MediaPickerSetup.Factory {
    override fun build(source: DataSource, isMultiSelectAllowed: Boolean): MediaPickerSetup {
        return when (source) {
            GIF_LIBRARY -> GifMediaPickerSetup.build(canMultiSelect = true)
            CAMERA -> DeviceMediaPickerSetup.buildCameraPicker()
            DEVICE -> DeviceMediaPickerSetup.buildMediaPicker(
                mediaTypes = IMAGES,
                canMultiSelect = true
            )
            SYSTEM_PICKER -> DeviceMediaPickerSetup.buildSystemPicker(
                mediaTypes = IMAGES,
                canMultiSelect = false
            )
            else -> throw InvalidParameterException("${source.name} source is not supported")
        }
    }
}
