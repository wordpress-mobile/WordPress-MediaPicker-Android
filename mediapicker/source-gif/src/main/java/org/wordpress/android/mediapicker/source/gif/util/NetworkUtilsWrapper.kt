package org.wordpress.android.mediapicker.source.gif.util

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.util.NetworkUtils
import javax.inject.Inject

@Reusable
class NetworkUtilsWrapper @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Returns true if a network connection is available.
     */
    fun isNetworkAvailable() = NetworkUtils.isNetworkAvailable(context)
}
