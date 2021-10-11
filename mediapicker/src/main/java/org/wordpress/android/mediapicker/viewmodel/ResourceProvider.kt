package org.wordpress.android.mediapicker.viewmodel

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getString(@StringRes resourceId: Int): String {
        return context.getString(resourceId)
    }

    fun getString(@StringRes resourceId: Int, vararg formatArgs: Any): String {
        return context.getString(resourceId, *formatArgs)
    }

    fun getPluralString(@PluralsRes pluralsId: Int, quantity: Int): String {
        return context.resources.getQuantityString(pluralsId, quantity, quantity)
    }

    fun getColor(@ColorRes resourceId: Int): Int {
        return ContextCompat.getColor(context, resourceId)
    }

    fun getDimensionPixelSize(@DimenRes dimen: Int): Int {
        val resources = context.resources
        return resources.getDimensionPixelSize(dimen)
    }
}
