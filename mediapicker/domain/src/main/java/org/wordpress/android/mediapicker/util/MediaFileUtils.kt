package org.wordpress.android.mediapicker.util

import android.annotation.TargetApi
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore.*
import androidx.annotation.RequiresApi
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.UTILS
import org.wordpress.android.util.MediaUtils
import java.io.File
import java.lang.Exception

object MediaFileUtils {
    @Suppress("Deprecation")
    @RequiresApi(VERSION_CODES.FROYO)
    @JvmStatic
    fun getExternalStorageDir(context: Context): File? {
        return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        }
    }

    fun getMediaStoreFilePath(context: Context, uri: Uri): String? {
        val path = getDocumentProviderPathKitkatOrHigher(context, uri)
        return path
            ?: if ("content".equals(uri.scheme, ignoreCase = true)) {
                MediaUtils.getDataColumn(context, uri, null as String?, null as Array<String?>?)
            } else {
                if ("file".equals(uri.scheme, ignoreCase = true)) uri.path else null
            }
    }

    @TargetApi(19)
    private fun getDocumentProviderPathKitkatOrHigher(context: Context, uri: Uri): String? {
        val isKitKat = VERSION.SDK_INT >= 19
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            var id: String?
            val split: Array<String>
            val type: String
            if (MediaUtils.isExternalStorageDocument(uri)) {
                id = DocumentsContract.getDocumentId(uri)
                split = id.split(":").toTypedArray()
                type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return getExternalStorageDir(context).toString() + "/" + split[1]
                }
            } else {
                if (MediaUtils.isDownloadsDocument(uri)) {
                    id = DocumentsContract.getDocumentId(uri)
                    if (id != null && id.startsWith("raw:")) {
                        return id.substring(4)
                    }
                    if (id != null && id.startsWith("msf:")) {
                        id = id.substring(4)
                    }
                    split = arrayOf(
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads",
                        "content://downloads/all_downloads"
                    )
                    val var14 = split.size
                    for (var13 in 0 until var14) {
                        val contentUriPrefix = split[var13]
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse(contentUriPrefix), java.lang.Long.valueOf(id)
                        )
                        try {
                            val path = MediaUtils.getDataColumn(
                                context,
                                contentUri,
                                null as String?,
                                null as Array<String?>?
                            )
                            if (path != null) {
                                return path
                            }
                        } catch (var11: Exception) {
                            AppLog.e(
                                UTILS,
                                "Error reading _data column for URI: $contentUri",
                                var11
                            )
                        }
                    }
                    return MediaUtils.downloadExternalMedia(context, uri).path
                }
                if (MediaUtils.isMediaDocument(uri)) {
                    id = DocumentsContract.getDocumentId(uri)
                    split = id.split(":").toTypedArray()
                    type = split[0]
                    val baseUri = if (VERSION.SDK_INT >= VERSION_CODES.Q /*29*/) {
                        when (type) {
                            "image" -> Images.Media.getContentUri(VOLUME_EXTERNAL)
                            "video" -> Video.Media.getContentUri(VOLUME_EXTERNAL)
                            "audio" -> Audio.Media.getContentUri(VOLUME_EXTERNAL)
                            else -> throw IllegalArgumentException("Cannot load media for selected type $type")
                        }
                    } else {
                        when (type) {
                            "image" -> Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> Audio.Media.EXTERNAL_CONTENT_URI
                            else -> throw IllegalArgumentException("Cannot load media for selected type $type")
                        }
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf<String?>(split[1])
                    return MediaUtils.getDataColumn(context, baseUri, selection, selectionArgs)
                }
            }
        }
        return null
    }
}