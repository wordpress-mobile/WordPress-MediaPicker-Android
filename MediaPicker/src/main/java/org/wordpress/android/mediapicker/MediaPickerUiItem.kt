package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaPickerUiItem.Type.AUDIO
import org.wordpress.android.mediapicker.MediaPickerUiItem.Type.DOCUMENT
import org.wordpress.android.mediapicker.MediaPickerUiItem.Type.PHOTO
import org.wordpress.android.mediapicker.MediaPickerUiItem.Type.VIDEO
import org.wordpress.android.util.UriWrapper

sealed class MediaPickerUiItem(
    val type: Type,
    open val uri: UriWrapper?,
    open val isSelected: Boolean,
    open val selectedOrder: Int?,
    open val showOrderCounter: Boolean
) {
    data class PhotoItem(
        override val uri: UriWrapper? = null,
        override val isSelected: Boolean = false,
        override val selectedOrder: Int? = null,
        override val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val clickAction: ClickAction
    ) : MediaPickerUiItem(PHOTO, uri, isSelected, selectedOrder, showOrderCounter)

    data class VideoItem(
        override val uri: UriWrapper? = null,
        override val isSelected: Boolean = false,
        override val selectedOrder: Int? = null,
        override val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val clickAction: ClickAction
    ) : MediaPickerUiItem(VIDEO, uri, isSelected, selectedOrder, showOrderCounter)

    data class AudioItem(
        override val uri: UriWrapper? = null,
        val fileName: String,
        val mimeType: String? = null,
        override val isSelected: Boolean = false,
        override val selectedOrder: Int? = null,
        override val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val clickAction: ClickAction
    ) : MediaPickerUiItem(AUDIO, uri, isSelected, selectedOrder, showOrderCounter)

    data class DocumentItem(
        override val uri: UriWrapper? = null,
        val fileName: String,
        val mimeType: String? = null,
        override val isSelected: Boolean = false,
        override val selectedOrder: Int? = null,
        override val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val clickAction: ClickAction
    ) : MediaPickerUiItem(DOCUMENT, uri, isSelected, selectedOrder, showOrderCounter)

    data class ToggleAction(
        val uri: UriWrapper,
        val canMultiselect: Boolean,
        private val toggleSelected: (uri: UriWrapper, canMultiselect: Boolean) -> Unit
    ) {
        fun toggle() = toggleSelected(uri, canMultiselect)
    }

    data class ClickAction(
        val uri: UriWrapper?,
        val isVideo: Boolean,
        private val clickItem: (uri: UriWrapper?, isVideo: Boolean) -> Unit
    ) {
        fun click() = clickItem(uri, isVideo)
    }

    enum class Type {
        PHOTO, VIDEO, AUDIO, DOCUMENT
    }
}
