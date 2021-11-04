package org.wordpress.android.mediapicker.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import androidx.annotation.RequiresApi
import org.wordpress.android.util.MediaUtils
import java.io.*

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
        var path: String? = null
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            try {
                val tempFileName = "temp-${System.currentTimeMillis()}.jpg"
                val cachedFile = File(context.cacheDir, tempFileName)
                val parcelFileDescriptor = context.contentResolver.openFile(uri, "r", null)
                parcelFileDescriptor?.fileDescriptor?.let { fd ->
                    val input = FileInputStream(fd)
                    val byteArray = readBinaryStream(
                        input,
                        parcelFileDescriptor.statSize.toInt()
                    )
                    val fileSaved = writeFile(cachedFile, byteArray)
                    if (fileSaved) {
                        path = cachedFile.absolutePath
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }else {
            path = MediaUtils.getRealPathFromURI(context, uri)
        }
        return path
    }

    private fun readBinaryStream(
        stream: InputStream,
        byteCount: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(if (byteCount > 0) byteCount else 4096)
            var read: Int
            while (stream.read(buffer).also { read = it } >= 0) {
                output.write(buffer, 0, read)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                stream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return output.toByteArray()
    }

    private fun writeFile(file: File, data: ByteArray): Boolean {
        return try {
            var output: BufferedOutputStream? = null
            try {
                output = BufferedOutputStream(FileOutputStream(file))
                output.write(data)
                output.flush()
                true
            } finally {
                output?.close()
            }
        } catch (ex: Exception) {
            false
        }
    }
}