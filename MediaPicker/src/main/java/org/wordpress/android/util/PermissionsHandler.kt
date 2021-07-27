package org.wordpress.android.util

import android.Manifest
import android.Manifest.permission
import android.content.Context
import org.wordpress.android.mediapicker.R
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionsHandler constructor(private val context: Context) {
    fun hasPermissionsToAccessPhotos(): Boolean {
        return hasCameraPermission() && hasStoragePermission()
    }

    fun hasStoragePermission(): Boolean {
        return hasReadStoragePermission() && hasWriteStoragePermission()
    }

    fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
                context, permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
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
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE -> context.getString(R.string.permission_storage)
            Manifest.permission.CAMERA -> context.getString(R.string.permission_camera)
            Manifest.permission.RECORD_AUDIO -> context.getString(R.string.permission_microphone)
            else -> {
                AppLog.w(AppLog.T.UTILS, "No name for requested permission")
                context.getString(R.string.unknown)
            }
        }
    }
}
