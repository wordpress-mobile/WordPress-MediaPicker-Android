package org.wordpress.android.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import org.wordpress.android.mediapicker.R

fun View.redirectContextClickToLongPressListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.setOnContextClickListener { it.performLongClick() }
    }
}

fun View?.scale(scaleStart: Float, scaleEnd: Float, duration: Long) {
    if (this == null) {
        return
    }
    val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, scaleStart, scaleEnd)
    val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleStart, scaleEnd)
    val animator = ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY)
    animator.duration = duration
    animator.interpolator = AccelerateDecelerateInterpolator()
    animator.start()
}

fun View?.startAnimation(aniResId: Int) {
    if (this == null) {
        return
    }
    val animation = AnimationUtils.loadAnimation(context, aniResId)
    startAnimation(animation)
}

fun View?.fadeIn(duration: Long) {
    this?.let { view ->
        ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f).apply {
            this.duration = duration
            this.interpolator = LinearInterpolator()
            this.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    view.visibility = View.VISIBLE
                }
            })
        }.start()
    }
}

fun View?.fadeOut(duration: Long) {
    this?.let { view ->
        ObjectAnimator.ofFloat(this, View.ALPHA, 1.0f, 0.0f).apply {
            this.duration = duration
            this.interpolator = LinearInterpolator()
            this.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
        }.start()
    }
}

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