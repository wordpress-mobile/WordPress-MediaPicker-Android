package org.wordpress.android.mediapicker.util

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.wordpress.android.mediapicker.util.AnimUtils.Duration.SHORT
import org.wordpress.android.mediapicker.util.UiString.UiStringRes
import org.wordpress.android.mediapicker.util.UiString.UiStringText

class UiHelpers {
    companion object {
        fun getTextOfUiString(context: Context, uiString: UiString): String =
            when (uiString) {
                is UiStringRes -> context.getString(uiString.stringRes)
                is UiStringText -> uiString.text
            }

        fun updateVisibility(view: View, visible: Boolean) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun setTextOrHide(view: TextView, uiString: UiString?) {
            val text = uiString?.let { getTextOfUiString(view.context, uiString) }
            setTextOrHide(view, text)
        }

        fun setTextOrHide(view: TextView, @StringRes resId: Int?) {
            val text = resId?.let { view.context.getString(resId) }
            setTextOrHide(view, text)
        }

        fun setTextOrHide(view: TextView, text: CharSequence?) {
            updateVisibility(view, text != null)
            text?.let {
                view.text = text
            }
        }

        fun setImageOrHide(imageView: ImageView, @DrawableRes resId: Int?) {
            updateVisibility(imageView, resId != null)
            resId?.let {
                imageView.setImageResource(resId)
            }
        }

        /**
         * Sets the [firstView] visible and the [secondView] invisible with a fade in/out animation and vice versa
         * @param visible if true the [firstView] is shown and the [secondView] is hidden else the other way round
         */
        fun fadeInfadeOutViews(firstView: View?, secondView: View?, visible: Boolean) {
            if (firstView == null || secondView == null || visible == (firstView.visibility == View.VISIBLE)) return
            if (visible) {
                AnimUtils.fadeIn(firstView, SHORT)
                AnimUtils.fadeOut(secondView, SHORT, View.INVISIBLE)
            } else {
                AnimUtils.fadeIn(secondView, SHORT)
                AnimUtils.fadeOut(firstView, SHORT, View.INVISIBLE)
            }
        }
    }
}
