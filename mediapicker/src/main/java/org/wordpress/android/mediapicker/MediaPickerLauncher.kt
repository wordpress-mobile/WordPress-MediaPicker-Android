package org.wordpress.android.mediapicker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.ui.MediaPickerActivity

class MediaPickerLauncher {
    companion object {
        fun buildMediaPickerIntent(
            activity: AppCompatActivity,
            setup: MediaPickerSetup
        ): Intent {
            return MediaPickerActivity.buildIntent(
                activity,
                setup
            )
        }
    }
}