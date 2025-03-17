package org.wordpress.android.mediapicker.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.redirectContextClickToLongPressListener() {
    this.setOnContextClickListener { it.performLongClick() }
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

@Suppress("Recycle")
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

@Suppress("Recycle")
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

inline fun View.doOnApplyWindowInsets(
    insetsMask: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    consumeInsets: Boolean = false,
    crossinline action: (Insets) -> Unit
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        action(insets.getInsets(insetsMask))
        if (consumeInsets) WindowInsetsCompat.CONSUMED else insets
    }
}
