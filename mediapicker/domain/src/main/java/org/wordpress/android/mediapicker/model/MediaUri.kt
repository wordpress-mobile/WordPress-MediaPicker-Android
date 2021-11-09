package org.wordpress.android.mediapicker.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaUri(val uri: String) : Parcelable {
    override fun toString(): String {
        return uri
    }

    fun asAndroidUri(): Uri {
        return Uri.parse(this.uri)
    }
}

fun Uri?.asMediaUri(): MediaUri {
    return MediaUri(this.toString())
}
