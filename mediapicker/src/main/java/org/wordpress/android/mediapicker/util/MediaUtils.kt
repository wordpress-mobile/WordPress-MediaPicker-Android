package org.wordpress.android.mediapicker.util

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import org.wordpress.android.mediapicker.R

object MediaUtils {
    fun getPlaceholder(context: Context, mimeType: String): Icon {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.getTypeInfo(mimeType).icon
        } else {
            val res = when {
                mimeType.startsWith("audio/") -> R.drawable.ic_audio_white_24dp
                mimeType.startsWith("video/") -> R.drawable.ic_video_camera_white_24dp
                mimeType.startsWith("image/") -> R.drawable.ic_image_white_24dp

                mimeType.contains("spreadsheet") ||
                        mimeType == "application/vnd.ms-excel" -> R.drawable.media_spreadsheet

                mimeType.contains("presentation") ||
                        mimeType == "application/vnd.ms-powerpoint" -> R.drawable.media_powerpoint

                else -> R.drawable.ic_pages_white_24dp
            }

            Icon.createWithResource(context, res)
        }
    }

    fun retrieveMediaUris(data: Intent): List<Uri?> {
        val clipData = data.clipData
        val uriList = ArrayList<Uri?>()
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                uriList.add(item.uri)
            }
        } else {
            uriList.add(data.data)
        }
        return uriList
    }
}
