package org.wordpress.android.mediapicker.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import kotlinx.coroutines.CoroutineScope
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.NextPageLoader
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.Type
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.VideoItem
import org.wordpress.android.mediapicker.ui.MediaPickerAdapterDiffCallback.Payload.COUNT_CHANGE
import org.wordpress.android.mediapicker.ui.MediaPickerAdapterDiffCallback.Payload.SELECTION_CHANGE
import org.wordpress.android.mediapicker.ui.viewholder.*
import org.wordpress.android.mediapicker.util.MediaThumbnailViewUtils

class MediaPickerAdapter internal constructor(
    private val coroutineScope: CoroutineScope
) : Adapter<ThumbnailViewHolder>() {
    private val thumbnailViewUtils = MediaThumbnailViewUtils()
    private var mediaList = listOf<MediaPickerUiItem>()

    fun loadData(result: List<MediaPickerUiItem>) {
        val diffResult = DiffUtil.calculateDiff(
            MediaPickerAdapterDiffCallback(mediaList, result)
        )
        mediaList = result
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int {
        return mediaList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        return when (viewType) {
            Type.PHOTO.ordinal -> PhotoThumbnailViewHolder(parent, thumbnailViewUtils)
            Type.VIDEO.ordinal -> VideoThumbnailViewHolder(parent, thumbnailViewUtils, coroutineScope)
            Type.FILE.ordinal -> FileThumbnailViewHolder(parent, thumbnailViewUtils)
            Type.NEXT_PAGE_LOADER.ordinal -> LoaderViewHolder(parent)
            else -> throw IllegalArgumentException("Unexpected view type")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return mediaList[position].type.ordinal
    }

    override fun onBindViewHolder(
        holder: ThumbnailViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val item = mediaList[position]
        var animateSelection = false
        var updateCount = false
        for (payload in payloads) {
            if (payload === SELECTION_CHANGE) {
                animateSelection = true
            }
            if (payload === COUNT_CHANGE) {
                updateCount = true
            }
        }
        when (item) {
            is PhotoItem -> (holder as PhotoThumbnailViewHolder).bind(item, animateSelection, updateCount)
            is VideoItem -> (holder as VideoThumbnailViewHolder).bind(item, animateSelection, updateCount)
            is FileItem -> (holder as FileThumbnailViewHolder).bind(item, animateSelection, updateCount)
            is NextPageLoader -> (holder as LoaderViewHolder).bind(item)
        }
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        onBindViewHolder(holder, position, listOf())
    }
}
