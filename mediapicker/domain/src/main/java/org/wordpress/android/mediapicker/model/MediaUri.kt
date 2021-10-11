package org.wordpress.android.mediapicker.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaUri(val uri: String) : Parcelable {
    override fun toString(): String {
        return uri
    }
}
