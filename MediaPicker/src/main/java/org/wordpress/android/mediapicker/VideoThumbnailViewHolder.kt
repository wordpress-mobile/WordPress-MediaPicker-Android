package org.wordpress.android.mediapicker

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import org.wordpress.android.mediapicker.R.id
import org.wordpress.android.mediapicker.util.cancelRequestAndClearImageView
import org.wordpress.android.mediapicker.util.loadThumbnailFromVideoUrl

/*
 * ViewHolder containing a device thumbnail
 */
class VideoThumbnailViewHolder(
    parent: ViewGroup,
    private val mediaThumbnailViewUtils: MediaThumbnailViewUtils,
    private val coroutineScope: CoroutineScope
) : ThumbnailViewHolder(
                parent,
                R.layout.media_picker_thumbnail_item
        ) {
    private val imgThumbnail: ImageView = itemView.findViewById(id.image_thumbnail)
    private val txtSelectionCount: TextView = itemView.findViewById(id.text_selection_count)
    private val videoOverlay: ImageView = itemView.findViewById(id.image_video_overlay)

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
        imgThumbnail.loadThumbnailFromVideoUrl(
            coroutineScope,
            item.url
        )
        mediaThumbnailViewUtils.setupListeners(
                imgThumbnail, item.isSelected,
                item.toggleAction,
                item.clickAction,
                animateSelection
        )
        mediaThumbnailViewUtils.setupVideoOverlay(videoOverlay, item.clickAction)
    }
}
