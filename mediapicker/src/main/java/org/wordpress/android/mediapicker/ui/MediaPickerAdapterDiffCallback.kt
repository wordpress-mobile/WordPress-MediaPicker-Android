package org.wordpress.android.mediapicker.ui

import androidx.recyclerview.widget.DiffUtil
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.NextPageLoader
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.VideoItem
import org.wordpress.android.mediapicker.model.toSelectableItem
import org.wordpress.android.mediapicker.ui.MediaPickerAdapterDiffCallback.Payload.COUNT_CHANGE
import org.wordpress.android.mediapicker.ui.MediaPickerAdapterDiffCallback.Payload.SELECTION_CHANGE

class MediaPickerAdapterDiffCallback(
    private val oldItems: List<MediaPickerUiItem>,
    private val updatedItems: List<MediaPickerUiItem>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldItems.size
    }

    override fun getNewListSize(): Int {
        return updatedItems.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldItems[oldItemPosition]
        val updatedItem = updatedItems[newItemPosition]
        return when {
            oldItem is PhotoItem && updatedItem is PhotoItem -> oldItem.identifier == updatedItem.identifier
            oldItem is VideoItem && updatedItem is VideoItem -> oldItem.identifier == updatedItem.identifier
            oldItem is FileItem && updatedItem is FileItem -> oldItem.identifier == updatedItem.identifier
            oldItem is NextPageLoader && updatedItem is NextPageLoader -> true
            else -> false
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition] == updatedItems[newItemPosition]
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        val oldItem = oldItems[oldItemPosition].toSelectableItem()
        val updatedItem = updatedItems[newItemPosition].toSelectableItem()
        if (oldItem != null && updatedItem != null) {
            if (oldItem.isSelected != updatedItem.isSelected) {
                return SELECTION_CHANGE
            }
            if (oldItem.showOrderCounter == updatedItem.showOrderCounter &&
                oldItem.selectedOrder != updatedItem.selectedOrder
            ) {
                return COUNT_CHANGE
            }
        }
        return super.getChangePayload(oldItemPosition, newItemPosition)
    }

    enum class Payload {
        SELECTION_CHANGE, COUNT_CHANGE
    }
}
