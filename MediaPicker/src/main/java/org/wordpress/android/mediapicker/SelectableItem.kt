package org.wordpress.android.mediapicker

import org.wordpress.android.mediapicker.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.MediaPickerUiItem.VideoItem

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
