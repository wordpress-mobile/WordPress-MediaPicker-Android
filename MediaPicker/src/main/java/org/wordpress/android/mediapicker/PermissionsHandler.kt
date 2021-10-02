package org.wordpress.android.mediapicker

import android.Manifest
import android.Manifest.permission
import android.content.Context
import org.wordpress.android.mediapicker.R
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.util.Log
import javax.inject.Inject

class PermissionsHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Log
) {
    fun hasPermissionsToAccessPhotos(): Boolean {
        return hasCameraPermission() && hasStoragePermission()
    }

    fun hasStoragePermission(): Boolean {
        return hasReadStoragePermission()
    }

    private fun hasReadStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
                context, permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
                context,
                permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /*
     * returns the name to display for a permission, ex: "permission.WRITE_EXTERNAL_STORAGE" > "Storage"
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> context.getString(R.string.permission_storage)
            Manifest.permission.CAMERA -> context.getString(R.string.permission_camera)
            Manifest.permission.RECORD_AUDIO -> context.getString(R.string.permission_microphone)
            else -> {
                log.w("No name for requested permission")
                context.getString(R.string.unknown)
            }
        }
    }
}
