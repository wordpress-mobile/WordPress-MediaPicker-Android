package org.wordpress.android.mediapicker.source.device

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore.Audio
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.Images
import android.provider.MediaStore.MediaColumns
import android.provider.MediaStore.VOLUME_EXTERNAL
import android.provider.MediaStore.Video
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.asMediaUri
import java.io.File
import javax.inject.Inject

class DeviceMediaLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun loadMedia(
        mediaType: MediaType,
        filter: String?,
        pageSize: Int,
        limitDate: Long? = null
    ): DeviceMediaList {
        val baseUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q /*29*/) {
            when (mediaType) {
                IMAGE -> Images.Media.getContentUri(VOLUME_EXTERNAL)
                VIDEO -> Video.Media.getContentUri(VOLUME_EXTERNAL)
                AUDIO -> Audio.Media.getContentUri(VOLUME_EXTERNAL)
                else -> throw IllegalArgumentException("Cannot load media for selected type $mediaType")
            }
        } else {
            when (mediaType) {
                IMAGE -> Images.Media.EXTERNAL_CONTENT_URI
                VIDEO -> Video.Media.EXTERNAL_CONTENT_URI
                AUDIO -> Audio.Media.EXTERNAL_CONTENT_URI
                else -> throw IllegalArgumentException("Cannot load media for selected type $mediaType")
            }
        }
        val result = mutableListOf<DeviceMediaItem>()
        val projection = arrayOf(ID_COL, ID_DATE_MODIFIED, ID_TITLE)
        val dateCondition = if (limitDate != null && limitDate != 0L) {
            "$ID_DATE_MODIFIED <= \'$limitDate\'"
        } else {
            null
        }
        val filterCondition = filter?.let { "$ID_TITLE LIKE \'%$filter%\'" }
        val condition = if (dateCondition != null && filterCondition != null) {
            "$dateCondition AND $filterCondition"
        } else {
            dateCondition ?: filterCondition
        } ?: ""

        val cursor = getCursor(condition, pageSize, baseUri, projection)
            ?: return DeviceMediaList(listOf(), null)

        try {
            val idIndex = cursor.getColumnIndexOrThrow(ID_COL)
            val dateIndex = cursor.getColumnIndexOrThrow(ID_DATE_MODIFIED)
            val titleIndex = cursor.getColumnIndexOrThrow(ID_TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val dateModified = cursor.getLong(dateIndex)
                val title = cursor.getString(titleIndex)
                val uri = Uri.withAppendedPath(baseUri, "" + id)
                val item = DeviceMediaItem(
                    uri.asMediaUri(),
                    title,
                    dateModified
                )
                result.add(item)
            }
        } finally {
            if (!cursor.isClosed) {
                cursor.close()
            }
        }

        val nextItem = if (result.size > pageSize) {
            result.last().dateModified
        } else {
            null
        }
        return DeviceMediaList(result.take(pageSize), nextItem)
    }

    fun loadDocuments(filter: String?, pageSize: Int, limitDate: Long? = null): DeviceMediaList {
        val storagePublicDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val nextPage = (storagePublicDirectory?.listFiles() ?: arrayOf()).filter {
            (limitDate == null || it.lastModifiedInSecs() <= limitDate) &&
                (filter == null || it.name.lowercase().contains(filter))
        }.sortedByDescending { it.lastModified() }.take(pageSize + 1)

        val nextItem = if (nextPage.size > pageSize) {
            nextPage.last().lastModifiedInSecs()
        } else {
            null
        }
        val result = nextPage.take(pageSize).map { file ->
            val uri = Uri.parse(file.toURI().toString())
            DeviceMediaItem(
                uri.asMediaUri(),
                file.name,
                file.lastModifiedInSecs()
            )
        }
        return DeviceMediaList(result, nextItem)
    }

    private fun getCursor(
        condition: String?,
        pageSize: Int,
        baseUri: Uri,
        projection: Array<String>
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q /*29*/) {
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, condition)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(FileColumns.DATE_MODIFIED)
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize + 1)
            putInt(ContentResolver.QUERY_ARG_OFFSET, 0)
        }
        context.contentResolver.query(
            baseUri,
            projection,
            bundle,
            null
        )
    } else {
        context.contentResolver.query(
            baseUri,
            projection,
            condition,
            null,
            "$ID_DATE_MODIFIED DESC LIMIT ${(pageSize + 1)}"
        )
    }

    private fun File.lastModifiedInSecs() = this.lastModified() / MILLIS

    fun getMimeType(mediaUri: MediaUri): String? {
        val uri = mediaUri.asAndroidUri()
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
        }
    }

    data class DeviceMediaList(val items: List<DeviceMediaItem>, val next: Long? = null)

    data class DeviceMediaItem(val mediaUri: MediaUri, val title: String, val dateModified: Long)

    companion object {
        private const val ID_COL = Images.Media._ID
        private const val ID_DATE_MODIFIED = MediaColumns.DATE_MODIFIED
        private const val ID_TITLE = MediaColumns.TITLE
        private const val MILLIS = 1000L
    }
}
