package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.Type.FILE
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.Type.NEXT_PAGE_LOADER
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.Type.PHOTO
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.Type.VIDEO

internal sealed class MediaPickerUiItem(
    val type: Type,
    val fullWidthItem: Boolean = false
) {
    data class PhotoItem(
        val url: String,
        val identifier: Identifier,
        val isSelected: Boolean = false,
        val selectedOrder: Int? = null,
        val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val longClickAction: LongClickAction
    ) : MediaPickerUiItem(PHOTO)

    data class VideoItem(
        val url: String,
        val identifier: Identifier,
        val isSelected: Boolean = false,
        val selectedOrder: Int? = null,
        val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val longClickAction: LongClickAction
    ) : MediaPickerUiItem(VIDEO)

    data class FileItem(
        val identifier: Identifier,
        val fileName: String,
        val fileExtension: String? = null,
        val isSelected: Boolean = false,
        val selectedOrder: Int? = null,
        val showOrderCounter: Boolean = false,
        val toggleAction: ToggleAction,
        val longClickAction: LongClickAction
    ) : MediaPickerUiItem(FILE)

    data class NextPageLoader(val isLoading: Boolean, val loadAction: () -> Unit) :
        MediaPickerUiItem(NEXT_PAGE_LOADER, fullWidthItem = true)

    data class ToggleAction(
        val identifier: Identifier,
        val canMultiselect: Boolean,
        private val toggleSelected: (identifier: Identifier, canMultiselect: Boolean) -> Unit
    ) {
        fun toggle() = toggleSelected(identifier, canMultiselect)
    }

    data class LongClickAction(
        val identifier: Identifier,
        val isVideo: Boolean,
        private val clickItem: (identifier: Identifier, isVideo: Boolean) -> Unit
    ) {
        fun click() = clickItem(identifier, isVideo)
    }

    enum class Type {
        PHOTO, VIDEO, FILE, NEXT_PAGE_LOADER
    }
}
