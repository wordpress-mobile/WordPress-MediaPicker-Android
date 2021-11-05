package org.wordpress.android.mediapicker

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.provider.MediaStore.MediaColumns
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wordpress.android.mediapicker.util.Log
import org.wordpress.android.mediapicker.util.MediaUtils
import java.io.File
import javax.inject.Inject

class MediaManager @Inject constructor(
    private val log: Log,
    @ApplicationContext private val applicationContext: Context
) {
    suspend fun addImageToMediaStore(path: String): Uri? {
        return if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            saveImageInQ(path)
        } else {
            saveImageLegacy(path)
        }
    }

    private fun saveImageLegacy(path: String): Uri? {
        MediaUtils.scanMediaFile(log, applicationContext, path)
        return Uri.parse(path)
    }

    @RequiresApi(VERSION_CODES.Q)
    private suspend fun saveImageInQ(path: String): Uri? {
        val uri: Uri?
        withContext(Dispatchers.IO) {
            val image = File(path)
            val contentValues = ContentValues().apply {
                put(MediaColumns.DISPLAY_NAME, image.name)
                put(
                    MediaColumns.MIME_TYPE,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(image.extension)
                )
                put(MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                put(MediaColumns.DATE_TAKEN, System.currentTimeMillis())
                put(MediaColumns.DATE_ADDED, System.currentTimeMillis())
                put(MediaColumns.IS_PENDING, 1)
            }

            val contentResolver = applicationContext.contentResolver
            uri = contentResolver.insert(Media.getContentUri(MediaStore.VOLUME_EXTERNAL), contentValues)
            if (uri != null) {
                val result = runCatching {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(image.readBytes())
                        outputStream.flush()
                        outputStream.close()
                    }
                }
                result.exceptionOrNull()?.let { log.e(it) }

                contentValues.clear()
                contentValues.put(MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        return uri
    }
}