package org.wordpress.android.mediapicker.util

import android.Manifest
import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wordpress.android.mediapicker.Key
import org.wordpress.android.mediapicker.Permissions
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.api.Log
import org.wordpress.android.mediapicker.api.Tracker
import org.wordpress.android.mediapicker.api.Tracker.Event
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.IMAGES
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.MUSIC
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.READ_STORAGE
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.VIDEOS
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.WRITE_STORAGE
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MediaPickerPermissionUtils @Inject constructor(
    private val perms: Permissions,
    private val log: Log,
    private val tracker: Tracker,
    @ApplicationContext private val context: Context
) {
    /**
     * called by the onRequestPermissionsResult() of various activities and fragments - tracks
     * the permission results, remembers that the permissions have been asked for
     *
     * @param permissionRequestResults list of results for above permissions
     */
    suspend fun persistPermissionRequestResults(
        permissionRequestResults: Map<String, Boolean>
    ) {
        permissionRequestResults.asIterable().forEach { permission ->
            getPermissionAskedKey(permission.key)?.let { key ->
                val isFirstTime = perms.wasPermissionAsked(key)
                trackPermissionResult(permission.key, permission.value, isFirstTime)
                perms.markAsAsked(key)
            }
        }
    }

    fun hasPermissionsToTakePhotos(): Boolean {
        return hasCameraPermission() && (VERSION.SDK_INT > VERSION_CODES.P || hasWriteStoragePermission())
    }

    val permissionsForTakingPhotos: List<PermissionsRequested>
        get() = if (VERSION.SDK_INT > VERSION_CODES.P) {
            listOf(CAMERA)
        } else {
            listOf(CAMERA, WRITE_STORAGE)
        }

    fun hasReadStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasImagesPermission(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
            context,
            permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
            context,
            permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasVideoPermission(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
            context,
            permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /*
     * returns true if we know the app has asked for the passed permission
     */
    @Suppress("ReturnCount")
    private suspend fun isPermissionAsked(context: Context, permission: String): Boolean {
        val key: Key = getPermissionAskedKey(permission) ?: return false

        // if the key exists, we've already stored whether this permission has been asked for
        if (perms.wasPermissionAsked(key)) {
            return true
        }

        // otherwise, check whether permission has already been granted - if so we know it has been asked
        if (ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            perms.markAsAsked(key)
            return true
        }
        return false
    }

    /*
     * returns true if the passed permission has been denied AND the user checked "never show again"
     * in the native permission dialog
     */
    suspend fun isPermissionAlwaysDenied(activity: Activity, permission: String): Boolean {
        // shouldShowRequestPermissionRationale returns false if the permission has been permanently
        // denied, but it also returns false if the app has never requested that permission - so we
        // check it only if we know we've asked for this permission
        if (isPermissionAsked(context, permission) &&
            ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_DENIED
        ) {
            val shouldShow =
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            return !shouldShow
        }
        return false
    }

    private fun trackPermissionResult(
        permission: String,
        isPermissionGranted: Boolean,
        isFirstTime: Boolean
    ) {
        val props: MutableMap<String, String> = HashMap()
        props["permission"] = permission
        props["is_first_time"] = java.lang.Boolean.toString(isFirstTime)
        if (isPermissionGranted) {
            tracker.track(Event.MEDIA_PERMISSION_GRANTED, props)
        } else {
            tracker.track(Event.MEDIA_PERMISSION_DENIED, props)
        }
    }

    /*
     * key in shared preferences which stores a boolean telling whether the app has already
     * asked for the passed permission
     */
    private fun getPermissionAskedKey(permission: String): Key? {
        return when (permission) {
            Manifest.permission.READ_EXTERNAL_STORAGE -> Permissions.PERMISSION_STORAGE_READ
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> Permissions.PERMISSION_STORAGE_WRITE
            Manifest.permission.CAMERA -> Permissions.PERMISSION_CAMERA
            Manifest.permission.READ_MEDIA_IMAGES -> Permissions.PERMISSION_IMAGES_READ
            Manifest.permission.READ_MEDIA_VIDEO -> Permissions.PERMISSION_VIDEO_READ
            Manifest.permission.READ_MEDIA_AUDIO -> Permissions.PERMISSION_AUDIO_READ
            else -> {
                log.w("No key for requested permission")
                null
            }
        }
    }

    /*
     * returns the name to display for a permission, ex: "permission.WRITE_EXTERNAL_STORAGE" > "Storage"
     */
    fun getPermissionName(permission: PermissionsRequested): String {
        val resource = when (permission) {
            WRITE_STORAGE,
            READ_STORAGE ->
                if (VERSION.SDK_INT > VERSION_CODES.Q)
                    R.string.permission_files_and_media
                else
                    R.string.permission_storage
            CAMERA -> R.string.permission_camera
            IMAGES -> R.string.permission_photos_videos
            VIDEOS -> R.string.permission_photos_videos
            MUSIC -> R.string.permission_audio
        }
        return context.getString(resource)
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
