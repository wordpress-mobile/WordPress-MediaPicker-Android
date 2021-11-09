package org.wordpress.android.mediapicker.ui.viewholder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.wordpress.android.mediapicker.R.id
import org.wordpress.android.mediapicker.R.layout
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.util.MediaThumbnailViewUtils

internal class FileThumbnailViewHolder(parent: ViewGroup, private val mediaThumbnailViewUtils: MediaThumbnailViewUtils) :
    ThumbnailViewHolder(parent, layout.media_picker_lib_file_item) {
    private val container: View = itemView.findViewById(id.media_grid_item_file_container)
    private val imgThumbnail: ImageView = itemView.findViewById(id.media_item_filetype_image)
    private val fileType: TextView = itemView.findViewById(id.media_item_filetype)
    private val fileName: TextView = itemView.findViewById(id.media_item_name)
    private val txtSelectionCount: TextView = itemView.findViewById(id.text_selection_count)

    fun bind(item: MediaPickerUiItem.FileItem, animateSelection: Boolean, updateCount: Boolean) {
        val isSelected = item.isSelected
        mediaThumbnailViewUtils.setupTextSelectionCount(
            txtSelectionCount,
            isSelected,
            item.selectedOrder,
            item.showOrderCounter,
            animateSelection
        )
        // Only count is updated so do not redraw the whole item
        if (updateCount) {
            return
        }
        mediaThumbnailViewUtils.setupFileImageView(
            container,
            imgThumbnail,
            item.fileName,
            item.isSelected,
            item.clickAction,
            item.toggleAction,
            animateSelection
        )
        fileType.text = item.fileExtension
        fileName.text = item.fileName
    }
}
