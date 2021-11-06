package org.wordpress.android.mediapicker.model

import org.wordpress.android.mediapicker.model.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.VideoItem

data class SelectableItem(val isSelected: Boolean, val showOrderCounter: Boolean, val selectedOrder: Int?)

fun MediaPickerUiItem.toSelectableItem(): SelectableItem? {
    return when (this) {
        is PhotoItem -> SelectableItem(
            this.isSelected,
            this.showOrderCounter,
            this.selectedOrder
        )
        is VideoItem -> SelectableItem(
            this.isSelected,
            this.showOrderCounter,
            this.selectedOrder
        )
        is FileItem -> SelectableItem(
            this.isSelected,
            this.showOrderCounter,
            this.selectedOrder
        )
        else -> null
    }
}
