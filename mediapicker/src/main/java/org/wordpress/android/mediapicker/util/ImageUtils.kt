package org.wordpress.android.mediapicker.util

import android.graphics.drawable.Icon
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import org.wordpress.android.mediapicker.R

fun ImageView.setImageResourceWithTint(
    icon: Icon,
    @ColorRes colorResId: Int
) {
    setImageIcon(icon)
    ImageViewCompat.setImageTintList(
        this,
        AppCompatResources.getColorStateList(context, colorResId)
    )
}

fun ImageView.announceSelectedImageForAccessibility(itemSelected: Boolean) {
    if (itemSelected) {
        announceForAccessibility(
            context.getString(R.string.media_picker_item_thumbnail_selected)
        )
    } else {
        announceForAccessibility(
            context.getString(R.string.media_picker_item_thumbnail_unselected)
        )
    }
}

fun ImageView.cancelRequestAndClearImageView() {
    Glide.with(context).clear(this)
}

fun ImageView.load(
    imgUrl: String,
    @DrawableRes drawableResId: Int
) {
    Glide.with(context)
        .load(imgUrl)
        .error(drawableResId)
        .placeholder(drawableResId)
        .fitCenter()
        .into(this)
        .clearOnDetach()
}
