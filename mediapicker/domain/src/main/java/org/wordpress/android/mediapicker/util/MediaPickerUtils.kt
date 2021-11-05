package org.wordpress.android.mediapicker.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.model.MediaPickerContext
import org.wordpress.android.util.MediaUtils
import java.io.*
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPickerUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    @Suppress("Deprecation")
    val externalStorageDir: File?
        get() {
            return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DCIM)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            }
        }

    private val isCameraAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun createSystemPickerIntent(
        openSystemPicker: OpenSystemPicker
    ): Intent? {
        val chooserContext: MediaPickerContext = openSystemPicker.pickerContext
        val intent = Intent(chooserContext.intentAction)
        intent.type = chooserContext.mediaTypeFilter
        intent.putExtra(Intent.EXTRA_MIME_TYPES, openSystemPicker.mimeTypes.toTypedArray<String>())
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
                    tempFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                return intent
            }
        }
        return null
    }

    fun getMediaStoreFilePath(context: Context, uri: Uri): String? {
        var path: String? = null
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            try {
                val cachedFile = createTempFile(context)
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
        }else {
            path = MediaUtils.getRealPathFromURI(context, uri)
        }
        return path
    }

    private fun createTempFile(context: Context): File? {
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