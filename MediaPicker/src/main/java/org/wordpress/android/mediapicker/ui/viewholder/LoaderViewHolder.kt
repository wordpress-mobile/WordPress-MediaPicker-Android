package org.wordpress.android.mediapicker.ui.viewholder

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams
import org.wordpress.android.mediapicker.R.id
import org.wordpress.android.mediapicker.R.layout
import org.wordpress.android.mediapicker.model.MediaPickerUiItem

class LoaderViewHolder(parent: ViewGroup) :
        ThumbnailViewHolder(parent, layout.media_picker_loader_item) {
    private val progress: View = itemView.findViewById(id.progress)
    private val retry: Button = itemView.findViewById(id.button)
    fun bind(item: MediaPickerUiItem.NextPageLoader) {
        setFullWidth()
        if (item.isLoading) {
            item.loadAction()
            progress.visibility = View.VISIBLE
            retry.visibility = View.GONE
        } else {
            progress.visibility = View.GONE
            retry.visibility = View.VISIBLE
            retry.setOnClickListener {
                item.loadAction()
            }
        }
    }

    private fun setFullWidth() {
        val layoutParams = itemView.layoutParams as? LayoutParams
        layoutParams?.isFullSpan = true
    }
}
