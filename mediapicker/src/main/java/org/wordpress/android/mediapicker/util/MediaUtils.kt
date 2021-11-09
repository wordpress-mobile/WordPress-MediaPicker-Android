package org.wordpress.android.mediapicker.util

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.api.Log
import org.wordpress.android.util.MediaUtils
import java.util.ArrayList

object MediaUtils {
    fun getPlaceholder(url: String?): Int {
        return when {
            MediaUtils.isValidImage(url) -> {
                drawable.ic_image_white_24dp
            }
            MediaUtils.isDocument(url) -> {
                drawable.ic_pages_white_24dp
            }
            MediaUtils.isPowerpoint(url) -> {
                drawable.media_powerpoint
            }
            MediaUtils.isSpreadsheet(url) -> {
                drawable.media_spreadsheet
            }
            MediaUtils.isVideo(url) -> {
                drawable.ic_video_camera_white_24dp
            }
            MediaUtils.isAudio(url) -> {
                drawable.ic_audio_white_24dp
            }
            else -> {
                drawable.ic_image_multiple_white_24dp
            }
        }
    }

    /*
     * Passes a newly-created media file to the media scanner service so it's available to
     * the media content provider - use this after capturing or downloading media to ensure
     * that it appears in the stock Gallery app
     */
    fun scanMediaFile(log: Log, context: Context, localMediaPath: String) {
        MediaScannerConnection.scanFile(
            context, arrayOf(localMediaPath), null
        ) { path: String, _: Uri? -> log.d("Media scanner finished scanning $path") }
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