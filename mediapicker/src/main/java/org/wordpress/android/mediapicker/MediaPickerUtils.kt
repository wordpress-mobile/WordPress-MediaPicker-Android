package org.wordpress.android.mediapicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
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
    companion object {
        private const val FOUR_KB = 4096
    }

    private val isCameraAvailable: Boolean
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
        if (openSystemPicker.pickerContext.intentAction == Intent.ACTION_OPEN_DOCUMENT) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        } catch (e: IOException) {
            log.e(e)
        }
        return file
    }

    fun generateCapturedImagePath(): String? = generateCapturedImageFile()?.path

    /**
     * Create an intent for capturing a device photo.
     *
     * The permission is requested in the source-camera module's AndroidManifest.xml.
     *
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun createCaptureImageIntent(tempFilePath: String): Intent? {
        return if (!isCameraAvailable) {
            null
        } else {
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
                }
            }
        }
    }

    fun getFilePath(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> getMediaStoreFilePath(uri)
            "file" -> uri.path
            else -> uri.toString()
        }
    }

    @Suppress("NestedBlockDepth")
    private fun getMediaStoreFilePath(uri: Uri): String? {
        var path: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cachedFile = createTempFile()
                context.contentResolver.openFile(uri, "r", null)
                    .use { parcelFileDescriptor ->
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
            context,
            arrayOf(localMediaPath),
            null
        ) { path: String, _: Uri? -> log.d("Media scanner finished scanning $path") }
    }

    @Suppress("Deprecation", "Recycle", "TooGenericExceptionCaught")
    private fun getLegacyMediaStorePath(uri: Uri): String? {
        val filePathColumn = arrayOf(Media.DATA)
        try {
            context.contentResolver.query(uri, filePathColumn, null, null, null)?.apply {
                if (moveToFirst()) {
                    val columnIndex: Int = getColumnIndex(filePathColumn[0])
                    return getString(columnIndex)
                }
                close()
            }
        } catch (e: RuntimeException) {
            log.e(e)
        }
        return null
    }

    private fun createTempFile(): File? {
        return try {
            File.createTempFile("temp-${System.currentTimeMillis()}", ".jpg")
        } catch (e: IOException) {
            log.e(e)
            null
        }
    }

    private fun readBinaryStream(
        stream: InputStream,
        byteCount: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(if (byteCount > 0) byteCount else FOUR_KB)
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
        } catch (e: IOException) {
            log.e(e)
            false
        }
    }
}
