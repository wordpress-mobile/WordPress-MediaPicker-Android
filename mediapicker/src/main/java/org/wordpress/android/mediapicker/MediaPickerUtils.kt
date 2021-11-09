package org.wordpress.android.mediapicker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.api.Log
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.model.MediaPickerContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPickerUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    val externalStorageDir: File?
        get() {
            return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
            } else {
                @Suppress("Deprecation")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            }
        }

    val isCameraAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun createSystemPickerIntent(
        openSystemPicker: OpenSystemPicker
    ): Intent? {
        val chooserContext: MediaPickerContext = openSystemPicker.pickerContext
        val intent = Intent(chooserContext.intentAction)
        intent.type = chooserContext.mediaTypeFilter
        intent.putExtra(Intent.EXTRA_MIME_TYPES, openSystemPicker.mimeTypes.toTypedArray())
        if (openSystemPicker.allowMultipleSelection) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return Intent.createChooser(intent, context.getString(chooserContext.title))
    }

    /**
     * Creates a temporary file for storing captured photos
     */
    private fun generateCapturedImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
        var file: File? = null
        try {
            file = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        } catch (e: RuntimeException) {
            log.e(e)
        }
        return file
    }

    fun generateCapturedImagePath(): String? = generateCapturedImageFile()?.path

    /**
     * Create an intent for capturing a device photo
     */
    fun createCaptureImageIntent(tempFilePath: String): Intent? {
        if (!isCameraAvailable) {
            return null
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            // Ensure that there's a camera activity to handle the intent
            intent.resolveActivity(context.packageManager)?.also {
                // capturedPhotoPath = file.absolutePath
                val authority = context.applicationContext.packageName + ".provider"
                val imageUri = FileProvider.getUriForFile(
                    context,
                    authority,
                    File(tempFilePath)
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                return intent
            }
        }
        return null
    }

    fun getMediaStoreFilePath(uri: Uri): String? {
        var path: String? = null
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            try {
                val cachedFile = createTempFile()
                val parcelFileDescriptor = context.contentResolver.openFile(uri, "r", null)
                parcelFileDescriptor?.fileDescriptor?.let { fd ->
                    val input = FileInputStream(fd)
                    val byteArray = readBinaryStream(
                        input,
                        parcelFileDescriptor.statSize.toInt()
                    )
                    if (cachedFile != null) {
                        val fileSaved = writeFile(cachedFile, byteArray)
                        if (fileSaved) {
                            path = cachedFile.absolutePath
                        }
                    }
                }
            } catch (e: IOException) {
                log.e(e)
            }
        } else {
            path = getLegacyMediaStorePath(uri)
        }
        return path
    }

    /*
     * Passes a newly-created media file to the media scanner service so it's available to
     * the media content provider - use this after capturing or downloading media to ensure
     * that it appears in the stock Gallery app
     */
    fun scanMediaFile(localMediaPath: String) {
        MediaScannerConnection.scanFile(
            context, arrayOf(localMediaPath), null
        ) { path: String, _: Uri? -> log.d("Media scanner finished scanning $path") }
    }

    private fun getLegacyMediaStorePath(uri: Uri): String? {
        @Suppress("Deprecation")
        val filePathColumn = arrayOf(Media.DATA)
        try {
            context.contentResolver.query(uri, filePathColumn, null, null, null)?.let { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex: Int = cursor.getColumnIndex(filePathColumn[0])
                    return cursor.getString(columnIndex)
                }
                cursor.close()
            }
        } catch (e: RuntimeException) {
            log.e(e)
        }
        return null
    }

    private fun createTempFile(): File? {
        var file: File? = null
        try {
            val tempFileName = "temp-${System.currentTimeMillis()}.jpg"
            file = File(context.cacheDir, tempFileName)
        } catch (e: RuntimeException) {
            log.e(e)
        }
        return file
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
            log.e(e)
        } finally {
            try {
                stream.close()
            } catch (e: IOException) {
                log.e(e)
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
        } catch (e: Exception) {
            log.e(e)
            false
        }
    }
}
