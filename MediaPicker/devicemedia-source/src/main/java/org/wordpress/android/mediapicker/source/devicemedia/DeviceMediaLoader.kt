package org.wordpress.android.mediapicker.source.devicemedia

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore.Audio
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.MediaColumns
import android.provider.MediaStore.Video
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.asAndroidUri
import org.wordpress.android.mediapicker.util.asMediaUri
import java.io.File

class DeviceMediaLoader(
    private val context: Context,
    private val mimeTypeSupportProvider: MimeTypeSupportProvider,
) {
    fun loadMedia(
        mediaType: MediaType,
        filter: String?,
        pageSize: Int,
        limitDate: Long? = null
    ): DeviceMediaList {
        val baseUri = when (mediaType) {
            IMAGE -> Media.EXTERNAL_CONTENT_URI
            VIDEO -> Video.Media.EXTERNAL_CONTENT_URI
            AUDIO -> Audio.Media.EXTERNAL_CONTENT_URI
            else -> throw IllegalArgumentException("Cannot load media for selected type $mediaType")
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

        val cursor = createCursor(
            context.contentResolver,
            baseUri,
            projection,
            condition,
            emptyArray(),
            ID_DATE_MODIFIED,
            false,
            pageSize + 1
        ) ?: return DeviceMediaList(listOf(), null)

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
        val storagePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

    private fun createCursor(
        contentResolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        whereCondition: String,
        selectionArgs: Array<String>,
        orderBy: String,
        orderAscending: Boolean,
        limit: Int = 20,
        offset: Int = 0
    ): Cursor? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            val selection = createSelectionBundle(whereCondition, selectionArgs, orderBy, orderAscending, limit, offset)
            contentResolver.query(collection, projection, selection, null)
        }
        else -> {
            val orderDirection = if (orderAscending) "ASC" else "DESC"
            var order = "$orderBy $orderDirection"
            order += " LIMIT $limit OFFSET $offset"
            contentResolver.query(collection, projection, whereCondition, selectionArgs, order)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createSelectionBundle(
        whereCondition: String,
        selectionArgs: Array<String>,
        orderBy: String,
        orderAscending: Boolean,
        limit: Int = 20,
        offset: Int = 0
    ): Bundle = Bundle().apply {
        // Limit & Offset
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        // Sort function
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(orderBy))
        // Sorting direction
        val orderDirection =
            if (orderAscending) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, orderDirection)
        // Selection
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, whereCondition)
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
    }

    private fun File.lastModifiedInSecs() = this.lastModified() / 1000

    fun getMimeType(mediaUri: MediaUri): String? {
        val uri = mediaUri.asAndroidUri()
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            mimeTypeSupportProvider.getMimeTypeForExtension(fileExtension)
        }
    }

    data class DeviceMediaList(val items: List<DeviceMediaItem>, val next: Long? = null)

    data class DeviceMediaItem(val mediaUri: MediaUri, val title: String, val dateModified: Long)

    companion object {
        private const val ID_COL = Media._ID
        private const val ID_DATE_MODIFIED = MediaColumns.DATE_MODIFIED
        private const val ID_TITLE = MediaColumns.TITLE
    }
}
