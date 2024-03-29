package org.wordpress.android.mediapicker.ui.viewholder

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.util.MediaThumbnailViewUtils
import org.wordpress.android.mediapicker.util.cancelRequestAndClearImageView

/*
 * ViewHolder containing a device thumbnail
 */
internal class VideoThumbnailViewHolder(
    parent: ViewGroup,
    private val mediaThumbnailViewUtils: MediaThumbnailViewUtils
) : ThumbnailViewHolder(
    parent,
    R.layout.media_picker_lib_thumbnail_item
) {
    private val imgThumbnail: ImageView = itemView.findViewById(R.id.image_thumbnail)
    private val txtSelectionCount: TextView = itemView.findViewById(R.id.text_selection_count)
    private val videoOverlay: ImageView = itemView.findViewById(R.id.image_video_overlay)

    fun bind(item: MediaPickerUiItem.VideoItem, animateSelection: Boolean, updateCount: Boolean) {
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
        imgThumbnail.cancelRequestAndClearImageView()
        mediaThumbnailViewUtils.setupListeners(
            imgThumbnail, item.isSelected,
            item.toggleAction,
            item.longClickAction,
            animateSelection
        )
        mediaThumbnailViewUtils.setupVideoOverlay(videoOverlay, item.longClickAction)
    }
}
