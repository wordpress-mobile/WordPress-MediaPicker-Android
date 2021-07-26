package org.wordpress.android.util

import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import org.wordpress.android.mediapicker.R

fun ImageView.setImageResourceWithTint(
    @DrawableRes drawableResId: Int,
    @ColorRes colorResId: Int
) {
    setImageDrawable(ContextCompat.getDrawable(context, drawableResId))
    ImageViewCompat.setImageTintList(
        this,
        AppCompatResources.getColorStateList(context, colorResId)
    )
}

fun ImageView.announceSelectedImageForAccessibility(itemSelected: Boolean) {
    if (itemSelected) {
        announceForAccessibility(
            context.getString(R.string.photo_picker_image_thumbnail_selected)
        )
    } else {
        announceForAccessibility(
            context.getString(R.string.photo_picker_image_thumbnail_unselected)
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
        .fitCenter()    // TODO: we might need to make this customisable based on the needs
        .into(this)
        .clearOnDetach()
}

fun ImageView.loadThumbnailFromVideoUrl(
    coroutineScope: CoroutineScope,
    videoUrl: String
) {
    // TODO: Anitaa: we would need to find a way to decouple VideoLoader since it uses FluxC internally
//    val context = this.context
//    val imageType = VIDEO
//    if (!context.isAvailable()) return
//    videoLoader?.runIfMediaNotTooBig(coroutineScope,
//        videoUrl,
//        loadAction = {
//            Glide.with(context)
//                .load(videoUrl)
//                .addFallback(imageType)
//                .addPlaceholder(imageType)
//                .applyScaleType(scaleType)
//                .attachRequestListener(requestListener)
//                .apply(RequestOptions().frame(0))
//                .into(imageView)
//                .clearOnDetach()
//        },
//        fallbackAction = {
//            if (!context.isAvailable()) return@runIfMediaNotTooBig
//            GlideApp.with(context)
//                .load(placeholderManager.getErrorResource(imageType))
//                .addPlaceholder(imageType)
//                .addFallback(imageType)
//                .into(imageView)
//                .clearOnDetach()
//        }) ?: throw java.lang.IllegalArgumentException("Video loader has to be set")
}