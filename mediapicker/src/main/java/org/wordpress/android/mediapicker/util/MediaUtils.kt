package org.wordpress.android.mediapicker.util

import android.content.Intent
import android.net.Uri
import org.wordpress.android.mediapicker.R
import org.wordpress.android.util.MediaUtils

object MediaUtils {
    fun getPlaceholder(url: String?): Int {
        return when {
            MediaUtils.isValidImage(url) -> {
                R.drawable.ic_image_white_24dp
            }
            MediaUtils.isDocument(url) -> {
                R.drawable.ic_pages_white_24dp
            }
            MediaUtils.isPowerpoint(url) -> {
                R.drawable.media_powerpoint
            }
            MediaUtils.isSpreadsheet(url) -> {
                R.drawable.media_spreadsheet
            }
            MediaUtils.isVideo(url) -> {
                R.drawable.ic_video_camera_white_24dp
            }
            MediaUtils.isAudio(url) -> {
                R.drawable.ic_audio_white_24dp
            }
            else -> {
                R.drawable.ic_image_multiple_white_24dp
            }
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
