package org.wordpress.android.mediapicker.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.LongClickAction
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.ToggleAction
import org.wordpress.android.util.ViewUtils
import java.util.*
import org.wordpress.android.mediapicker.api.R as MPApiR

internal class MediaThumbnailViewUtils {
    fun setupListeners(
        imgThumbnail: ImageView,
        isSelected: Boolean,
        toggleAction: ToggleAction,
        longClickAction: LongClickAction,
        animateSelection: Boolean
    ) {
        imgThumbnail.setOnClickListener {
            toggleAction.toggle()
            imgThumbnail.announceSelectedImageForAccessibility(isSelected)
        }
        imgThumbnail.setOnLongClickListener {
            longClickAction.click()
            true
        }
        imgThumbnail.redirectContextClickToLongPressListener()
        displaySelection(animateSelection, isSelected, imgThumbnail)
    }

    @Suppress("LongParameterList")
    fun setupFileImageView(
        container: View,
        imgThumbnail: ImageView,
        mimeType: String?,
        isSelected: Boolean,
        longClickAction: LongClickAction,
        toggleAction: ToggleAction,
        animateSelection: Boolean
    ) {
        imgThumbnail.cancelRequestAndClearImageView()

        // not an image or video, so show file name and file type
        val placeholderIcon = MediaUtils.getPlaceholder(container.context, mimeType.orEmpty())
        imgThumbnail.setImageResourceWithTint(placeholderIcon, MPApiR.color.neutral_30)

        container.setOnClickListener {
            toggleAction.toggle()
            imgThumbnail.announceSelectedImageForAccessibility(isSelected)
        }
        container.setOnLongClickListener {
            longClickAction.click()
            true
        }
        container.redirectContextClickToLongPressListener()
        displaySelection(animateSelection, isSelected, container)
    }

    private fun displaySelection(animate: Boolean, isSelected: Boolean, view: View) {
        if (animate) {
            val duration = view.context.resources.getInteger(ANI_DURATION).toLong()
            if (isSelected) {
                view.scale(SCALE_NORMAL, SCALE_SELECTED, duration)
            } else {
                view.scale(SCALE_SELECTED, SCALE_NORMAL, duration)
            }
        } else {
            val scale = if (isSelected) SCALE_SELECTED else SCALE_NORMAL
            if (view.scaleX != scale) {
                view.scaleX = scale
                view.scaleY = scale
            }
        }
    }

    fun displayTextSelectionCount(
        animate: Boolean,
        showOrderCounter: Boolean,
        isSelected: Boolean,
        txtSelectionCount: TextView
    ) {
        if (animate) {
            val duration = txtSelectionCount.context.resources.getInteger(ANI_DURATION).toLong()
            when {
                showOrderCounter -> txtSelectionCount.startAnimation(R.anim.pop)
                isSelected -> txtSelectionCount.fadeIn(duration)
                else -> txtSelectionCount.fadeOut(duration)
            }
        } else {
            txtSelectionCount.visibility = if (showOrderCounter || isSelected) View.VISIBLE else View.GONE
        }
    }

    fun updateSelectionCountForPosition(
        txtSelectionCount: TextView,
        selectedOrder: Int?
    ) {
        if (selectedOrder != null) {
            txtSelectionCount.text = String.format(Locale.getDefault(), "%d", selectedOrder)
        } else {
            txtSelectionCount.text = null
        }
    }

    fun setupTextSelectionCount(
        txtSelectionCount: TextView,
        isSelected: Boolean,
        selectedOrder: Int?,
        showOrderCounter: Boolean,
        animateSelection: Boolean
    ) {
        ViewUtils.addCircularShadowOutline(txtSelectionCount)
        txtSelectionCount.isSelected = isSelected
        updateSelectionCountForPosition(txtSelectionCount, selectedOrder)
        if (!showOrderCounter) {
            txtSelectionCount.setBackgroundResource(R.drawable.media_picker_circle_pressed)
        }
        displayTextSelectionCount(
            animateSelection,
            showOrderCounter,
            isSelected,
            txtSelectionCount
        )
    }

    fun setupVideoOverlay(videoOverlay: ImageView, longClickAction: LongClickAction) {
        videoOverlay.visibility = View.VISIBLE
        videoOverlay.setOnClickListener { longClickAction.click() }
    }

    companion object {
        private const val SCALE_NORMAL = 1.0f
        private const val SCALE_SELECTED = .8f
        private val ANI_DURATION = R.integer.config_shortAnimTime
    }
}
