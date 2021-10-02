package org.wordpress.android.mediapicker.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.wordpress.android.mediapicker.viewmodel.ResourceProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.text.Html
import android.content.Intent
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.appcompat.app.AlertDialog.Builder
import androidx.core.app.ActivityCompat
import org.wordpress.android.mediapicker.Key
import org.wordpress.android.mediapicker.Permissions
import org.wordpress.android.mediapicker.R.string
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPickerPermissionUtils @Inject constructor(
    private val perms: Permissions,
    private val log: Log
) {
    // permission request codes - note these are reported to analytics so they shouldn't be changed
    companion object {
        const val SHARE_MEDIA_PERMISSION_REQUEST_CODE = 10
        const val MEDIA_BROWSER_PERMISSION_REQUEST_CODE = 20
        const val MEDIA_PREVIEW_PERMISSION_REQUEST_CODE = 30
        const val PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE = 40
        const val PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE = 41
        const val EDITOR_LOCATION_PERMISSION_REQUEST_CODE = 50
        const val EDITOR_MEDIA_PERMISSION_REQUEST_CODE = 60
        const val EDITOR_DRAG_DROP_PERMISSION_REQUEST_CODE = 70
        const val READER_FILE_DOWNLOAD_PERMISSION_REQUEST_CODE = 80
    }

    /**
     * called by the onRequestPermissionsResult() of various activities and fragments - tracks
     * the permission results, remembers that the permissions have been asked for, and optionally
     * shows a dialog enabling the user to edit permissions if any are always denied
     *
     * @param activity host activity
     * @param requestCode request code passed to ContextCompat.checkSelfPermission
     * @param permissions list of permissions
     * @param grantResults list of results for above permissions
     * @param checkForAlwaysDenied show dialog if any permissions always denied
     * @return true if all permissions granted
     */
    fun setPermissionListAsked(
        activity: Activity,
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        checkForAlwaysDenied: Boolean
    ): Boolean {
        for (i in permissions.indices) {
            getPermissionAskedKey(permissions[i])?.let { key ->
                val isFirstTime = perms.wasPermissionAsked(key)
                trackPermissionResult(requestCode, permissions[i], grantResults[i], isFirstTime)
                perms.markAsAsked(key)
            }
        }
        var allGranted = true
        for (i in grantResults.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                allGranted = false
                if (checkForAlwaysDenied
                    && !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        permissions[i]
                    )
                ) {
                    showPermissionAlwaysDeniedDialog(activity, permissions[i])
                    break
                }
            }
        }
        return allGranted
    }

    /*
     * returns true if we know the app has asked for the passed permission
     */
    private fun isPermissionAsked(context: Context, permission: String): Boolean {
        val key: Key = getPermissionAskedKey(permission)
            ?: return false

        // if the key exists, we've already stored whether this permission has been asked for
        if (perms.wasPermissionAsked(key)) {
            return true
        }

        // otherwise, check whether permission has already been granted - if so we know it has been asked
        if (ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED) {
            perms.markAsAsked(key)
            return true
        }
        return false
    }

    /*
     * returns true if the passed permission has been denied AND the user checked "never show again"
     * in the native permission dialog
     */
    fun isPermissionAlwaysDenied(activity: Activity, permission: String): Boolean {
        // shouldShowRequestPermissionRationale returns false if the permission has been permanently
        // denied, but it also returns false if the app has never requested that permission - so we
        // check it only if we know we've asked for this permission
        if (isPermissionAsked(activity, permission) &&
            ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_DENIED
        ) {
            val shouldShow =
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            return !shouldShow
        }
        return false
    }

    private fun trackPermissionResult(
        requestCode: Int,
        permission: String,
        result: Int,
        isFirstTime: Boolean
    ) {
        val props: MutableMap<String, String> = HashMap()
        props["permission"] = permission
        props["request_code"] = requestCode.toString()
        props["is_first_time"] = java.lang.Boolean.toString(isFirstTime)
        if (result == PackageManager.PERMISSION_GRANTED) {
//            AnalyticsTracker.track(AnalyticsTracker.Stat.APP_PERMISSION_GRANTED, props)
        } else if (result == PackageManager.PERMISSION_DENIED) {
//            AnalyticsTracker.track(AnalyticsTracker.Stat.APP_PERMISSION_DENIED, props)
        }
    }

    /*
     * key in shared preferences which stores a boolean telling whether the app has already
     * asked for the passed permission
     */
    private fun getPermissionAskedKey(permission: String): Key? {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> Permissions.PERMISSION_STORAGE_READ
            Manifest.permission.CAMERA -> Permissions.PERMISSION_CAMERA
            else -> {
                log.w("No key for requested permission")
                null
            }
        }
    }

    /*
     * returns the name to display for a permission, ex: "permission.WRITE_EXTERNAL_STORAGE" > "Storage"
     */
    fun getPermissionName(context: Context, permission: String): String {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> context.getString(
                string.permission_storage
            )
            Manifest.permission.CAMERA -> context.getString(string.permission_camera)
            Manifest.permission.RECORD_AUDIO -> context.getString(string.permission_microphone)
            else -> {
                log.w("No name for requested permission")
                context.getString(string.unknown)
            }
        }
    }

    /*
     * returns the name to display for a permission, ex: "permission.WRITE_EXTERNAL_STORAGE" > "Storage"
     */
    fun getPermissionName(resourceProvider: ResourceProvider, permission: String): String {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> resourceProvider.getString(
                string.permission_storage
            )
            Manifest.permission.CAMERA -> resourceProvider.getString(string.permission_camera)
            Manifest.permission.RECORD_AUDIO -> resourceProvider.getString(string.permission_microphone)
            else -> {
                log.w("No name for requested permission")
                resourceProvider.getString(string.unknown)
            }
        }
    }

    /*
     * called when the app detects that the user has permanently denied a permission, shows a dialog
     * alerting them to this fact and enabling them to visit the app settings to edit permissions
     */
    fun showPermissionAlwaysDeniedDialog(context: Context, permission: String) {
        val message = String.format(
            context.getString(string.permissions_denied_message),
            getPermissionName(context, permission)
        )
        val builder: Builder = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(string.permissions_denied_title))
            .setMessage(Html.fromHtml(message))
            .setPositiveButton(
                string.button_edit_permissions
            ) { _, _ -> showAppSettings(context) }
            .setNegativeButton(string.button_not_now, null)
        builder.show()
    }

    /*
     * open the device's settings page for this app so the user can edit permissions
     */
    fun showAppSettings(context: Context) {
        val intent = Intent()
        intent.action = ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}