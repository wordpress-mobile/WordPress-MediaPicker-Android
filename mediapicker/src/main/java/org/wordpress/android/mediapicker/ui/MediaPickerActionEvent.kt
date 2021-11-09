package org.wordpress.android.mediapicker.ui

import android.os.Bundle
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.MediaPickerActionType.ANDROID_CHOOSE_FROM_DEVICE
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.MediaPickerActionType.CAPTURE_PHOTO
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.MediaPickerActionType.SWITCH_SOURCE

internal sealed class MediaPickerActionEvent(val type: MediaPickerActionType) {
    companion object {
        private const val KEY_LAST_TAPPED_ICON = "last_tapped_icon"
        private const val KEY_LAST_TAPPED_ICON_ALLOWED_TYPES = "last_tapped_icon_allowed_types"
        private const val KEY_LAST_TAPPED_ICON_DATA_SOURCE = "last_tapped_icon_data_source"

        @JvmStatic
        fun fromBundle(bundle: Bundle): MediaPickerActionEvent? {
            val iconTypeName = bundle.getString(KEY_LAST_TAPPED_ICON) ?: return null

            return when (
                iconTypeName.let {
                    MediaPickerActionType.fromNameString(
                        iconTypeName
                    )
                }
            ) {
                ANDROID_CHOOSE_FROM_DEVICE -> {
                    val allowedTypes = (
                        bundle.getStringArrayList(
                            KEY_LAST_TAPPED_ICON_ALLOWED_TYPES
                        )
                            ?: listOf<String>()
                        ).map {
                        MediaType.valueOf(it)
                    }.toSet()
                    ChooseFromAndroidDevice(allowedTypes)
                }
                CAPTURE_PHOTO -> CapturePhoto
                SWITCH_SOURCE -> {
                    val ordinal = bundle.getInt(KEY_LAST_TAPPED_ICON_DATA_SOURCE, -1)
                    if (ordinal != -1) {
                        val dataSource = DataSource.values()[ordinal]
                        SwitchSource(dataSource)
                    } else {
                        null
                    }
                }
            }
        }
    }

    data class ChooseFromAndroidDevice(
        val allowedTypes: Set<MediaType>
    ) : MediaPickerActionEvent(ANDROID_CHOOSE_FROM_DEVICE)

    data class SwitchSource(val dataSource: DataSource) : MediaPickerActionEvent(SWITCH_SOURCE)

    object CapturePhoto : MediaPickerActionEvent(CAPTURE_PHOTO)

    fun toBundle(bundle: Bundle) {
        bundle.putString(KEY_LAST_TAPPED_ICON, type.name)
        when (this) {
            is ChooseFromAndroidDevice -> {
                bundle.putStringArrayList(
                    KEY_LAST_TAPPED_ICON_ALLOWED_TYPES,
                    ArrayList(allowedTypes.map { it.name })
                )
            }
            is SwitchSource -> {
                bundle.putInt(KEY_LAST_TAPPED_ICON_DATA_SOURCE, this.dataSource.ordinal)
            }
            CapturePhoto -> { }
        }
    }

    enum class MediaPickerActionType {
        ANDROID_CHOOSE_FROM_DEVICE,
        SWITCH_SOURCE,
        CAPTURE_PHOTO;

        companion object {
            @JvmStatic
            fun fromNameString(actionTypeName: String): MediaPickerActionType {
                return values().firstOrNull { it.name == actionTypeName }
                    ?: throw IllegalArgumentException("MediaPickerActionType not found with name $actionTypeName")
            }
        }
    }
}
