package org.wordpress.android.mediapicker

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import org.wordpress.android.mediapicker.MediaPickerSetup.CameraSetup.HIDDEN
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO

class MediaPickerLauncher {
    companion object {
        fun buildMediaPickerIntent(
            activity: AppCompatActivity,
            isImagePicker: Boolean,
            isVideoPicker: Boolean,
            canMultiSelect: Boolean
        ): Intent {
            return MediaPickerActivity.buildIntent(
                activity,
                buildLocalMediaPickerSetup(isImagePicker, isVideoPicker, canMultiSelect)
            )
        }

        private fun buildLocalMediaPickerSetup(
            isImagePicker: Boolean,
            isVideoPicker: Boolean,
            canMultiSelect: Boolean
        ): MediaPickerSetup {
            val allowedTypes = mutableSetOf<MediaType>()
            if (isImagePicker) {
                allowedTypes.add(IMAGE)
            }
            if (isVideoPicker) {
                allowedTypes.add(VIDEO)
            }
            val title = if (isImagePicker && isVideoPicker) {
                R.string.photo_picker_photo_or_video_title
            } else if (isVideoPicker) {
                R.string.photo_picker_video_title
            } else {
                R.string.photo_picker_title
            }
            return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(),
                canMultiselect = canMultiSelect,
                requiresStoragePermissions = true,
                allowedTypes = allowedTypes,
                cameraSetup = HIDDEN,
                systemPickerEnabled = true,
                editingEnabled = isImagePicker,
                queueResults = false,
                defaultSearchView = false,
                title = title
            )
        }
    }
}
/*
    fun showStoriesPhotoPickerForResultAndTrack(activity: Activity, site: SiteModel?) {
        analyticsTrackerWrapper.track(Stat.MEDIA_PICKER_OPEN_FOR_STORIES)
        showStoriesPhotoPickerForResult(activity, site)
    }

    fun showStoriesPhotoPickerForResult(
        activity: Activity,
        site: SiteModel?
    ) {
        ActivityLauncher.showPhotoPickerForResult(activity, WP_STORIES_MEDIA_PICKER, site, null)
    }

    fun showGravatarPicker(fragment: Fragment) {
        val mediaPickerSetup = MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(),
                canMultiselect = false,
                requiresStoragePermissions = true,
                allowedTypes = setOf(IMAGE),
                cameraSetup = ENABLED,
                systemPickerEnabled = true,
                editingEnabled = true,
                queueResults = false,
                defaultSearchView = false,
                title = R.string.photo_picker_title
        )
        val intent = MediaPickerActivity.buildIntent(
                fragment.requireContext(),
                mediaPickerSetup
        )
        fragment.startActivityForResult(intent, RequestCodes.PHOTO_PICKER)
    }

    fun showFilePicker(activity: Activity, canMultiselect: Boolean = true, site: SiteModel) {
        showFilePicker(
                activity,
                site,
                canMultiselect,
                mutableSetOf(IMAGE, VIDEO, AUDIO, DOCUMENT),
                RequestCodes.FILE_LIBRARY,
                R.string.photo_picker_choose_file
        )
    }

    fun showAudioFilePicker(activity: Activity, canMultiselect: Boolean = false, site: SiteModel) {
        showFilePicker(
                activity,
                site,
                canMultiselect,
                mutableSetOf(AUDIO),
                RequestCodes.AUDIO_LIBRARY,
                R.string.photo_picker_choose_audio
        )
    }

    @Suppress("LongParameterList")
    private fun showFilePicker(
        activity: Activity,
        site: SiteModel,
        canMultiselect: Boolean = false,
        allowedTypes: Set<MediaType>,
        requestCode: Int,
        @StringRes title: Int
    ) {
        val mediaPickerSetup = MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = setOf(),
                canMultiselect = canMultiselect,
                requiresStoragePermissions = true,
                allowedTypes = allowedTypes,
                cameraSetup = HIDDEN,
                systemPickerEnabled = true,
                editingEnabled = true,
                queueResults = false,
                defaultSearchView = false,
                title = title
        )
        val intent = MediaPickerActivity.buildIntent(
                activity,
                mediaPickerSetup,
                site
        )
        activity.startActivityForResult(
                intent,
                requestCode
        )
    }

    fun viewWPMediaLibraryPickerForResult(activity: Activity, site: SiteModel, browserType: MediaBrowserType) {
        val intent = MediaPickerActivity.buildIntent(
                activity,
                buildWPMediaLibraryPickerSetup(browserType),
                site
        )
        val requestCode: Int = if (browserType.canMultiselect()) {
            RequestCodes.MULTI_SELECT_MEDIA_PICKER
        } else {
            RequestCodes.SINGLE_SELECT_MEDIA_PICKER
        }
        activity.startActivityForResult(intent, requestCode)
    }

    fun showStockMediaPickerForResult(
        activity: Activity,
        site: SiteModel,
        requestCode: Int,
        allowMultipleSelection: Boolean
    ) {
        val mediaPickerSetup = MediaPickerSetup(
                primaryDataSource = STOCK_LIBRARY,
                availableDataSources = setOf(),
                canMultiselect = allowMultipleSelection,
                requiresStoragePermissions = false,
                allowedTypes = setOf(IMAGE),
                cameraSetup = HIDDEN,
                systemPickerEnabled = false,
                editingEnabled = false,
                queueResults = false,
                defaultSearchView = true,
                title = R.string.photo_picker_stock_media
        )
        val intent = MediaPickerActivity.buildIntent(
                activity,
                mediaPickerSetup,
                site
        )
        activity.startActivityForResult(intent, requestCode)
    }

    fun showGifPickerForResult(
        activity: Activity,
        site: SiteModel,
        allowMultipleSelection: Boolean
    ) {
        val requestCode = if (allowMultipleSelection) {
            RequestCodes.GIF_PICKER_MULTI_SELECT
        } else {
            RequestCodes.GIF_PICKER_SINGLE_SELECT
        }
        val mediaPickerSetup = MediaPickerSetup(
                primaryDataSource = GIF_LIBRARY,
                availableDataSources = setOf(),
                canMultiselect = allowMultipleSelection,
                requiresStoragePermissions = false,
                allowedTypes = setOf(IMAGE),
                cameraSetup = HIDDEN,
                systemPickerEnabled = false,
                editingEnabled = false,
                queueResults = false,
                defaultSearchView = true,
                title = R.string.photo_picker_gif
        )
        val intent = MediaPickerActivity.buildIntent(
                activity,
                mediaPickerSetup,
                site
        )
        activity.startActivityForResult(intent, requestCode)
    }

    private fun buildLocalMediaPickerSetup(browserType: MediaBrowserType): MediaPickerSetup {
        val allowedTypes = mutableSetOf<MediaType>()
        if (browserType.isImagePicker) {
            allowedTypes.add(IMAGE)
        }
        if (browserType.isVideoPicker) {
            allowedTypes.add(VIDEO)
        }
        val title = if (browserType.isImagePicker && browserType.isVideoPicker) {
            R.string.photo_picker_photo_or_video_title
        } else if (browserType.isVideoPicker) {
            R.string.photo_picker_video_title
        } else {
            R.string.photo_picker_title
        }
        return MediaPickerSetup(
                primaryDataSource = DEVICE,
                availableDataSources = if (browserType.isWPStoriesPicker) setOf(WP_LIBRARY) else setOf(),
                canMultiselect = browserType.canMultiselect(),
                requiresStoragePermissions = true,
                allowedTypes = allowedTypes,
                cameraSetup = if (browserType.isWPStoriesPicker) STORIES else HIDDEN,
                systemPickerEnabled = true,
                editingEnabled = browserType.isImagePicker,
                queueResults = browserType == FEATURED_IMAGE_PICKER,
                defaultSearchView = false,
                title = title
        )
    }

    private fun buildWPMediaLibraryPickerSetup(browserType: MediaBrowserType): MediaPickerSetup {
        val allowedTypes = mutableSetOf<MediaType>()
        if (browserType.isImagePicker) {
            allowedTypes.add(IMAGE)
        }
        if (browserType.isVideoPicker) {
            allowedTypes.add(VIDEO)
        }

        if (browserType.isAudioPicker) {
            allowedTypes.add(AUDIO)
        }

        if (browserType.isDocumentPicker) {
            allowedTypes.add(DOCUMENT)
        }

        return MediaPickerSetup(
                primaryDataSource = WP_LIBRARY,
                availableDataSources = setOf(),
                canMultiselect = browserType.canMultiselect(),
                requiresStoragePermissions = false,
                allowedTypes = allowedTypes,
                cameraSetup = if (browserType.isWPStoriesPicker) STORIES else HIDDEN,
                systemPickerEnabled = false,
                editingEnabled = false,
                queueResults = false,
                defaultSearchView = false,
                title = R.string.wp_media_title
        )
    }
}
*/