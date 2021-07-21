package org.wordpress.android.mediapicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.IMAGE_EDITOR_EDIT_IMAGE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_LAUNCH_WPSTORIES_CAMERA_REQUESTED
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_ID
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_QUEUED_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_SOURCE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.RESULT_IDS
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaPickerActivity.MediaPickerMediaSource.ANDROID_CAMERA
import org.wordpress.android.mediapicker.MediaPickerActivity.MediaPickerMediaSource.APP_PICKER
import org.wordpress.android.mediapicker.MediaPickerFragment.Companion.newInstance
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenCameraForWPStories
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerListener
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.MEDIA_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.PHOTO_PICKER
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.TAKE_PHOTO
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.STOCK_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerSetup.DataSource.WP_LIBRARY
import org.wordpress.android.mediapicker.databinding.PhotoPickerActivityBinding
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.MEDIA
import org.wordpress.android.util.WPMediaUtils
import java.io.File

class MediaPickerActivity : AppCompatActivity(), MediaPickerListener {
    private var mediaCapturePath: String? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    enum class MediaPickerMediaSource {
        ANDROID_CAMERA, ANDROID_PICKER, APP_PICKER, WP_MEDIA_PICKER, STOCK_MEDIA_PICKER;

        companion object {
            fun fromString(strSource: String?): MediaPickerMediaSource? {
                if (strSource != null) {
                    for (source in values()) {
                        if (source.name.equals(strSource, ignoreCase = true)) {
                            return source
                        }
                    }
                }
                return null
            }

            fun fromDataSource(dataSource: DataSource): MediaPickerMediaSource {
                return when (dataSource) {
                    DEVICE -> APP_PICKER
                    WP_LIBRARY -> WP_MEDIA_PICKER
                    STOCK_LIBRARY -> STOCK_MEDIA_PICKER
                    GIF_LIBRARY -> APP_PICKER
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = PhotoPickerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarMain.setNavigationIcon(R.drawable.ic_close_white_24dp)
        setSupportActionBar(binding.toolbarMain)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowTitleEnabled(true)
        }
        if (savedInstanceState == null) {
            mediaPickerSetup = MediaPickerSetup.fromIntent(intent)
        } else {
            mediaPickerSetup = MediaPickerSetup.fromBundle(savedInstanceState)
        }
        var fragment = pickerFragment
        if (fragment == null) {
            fragment = newInstance(this, mediaPickerSetup)
            supportFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container,
                            fragment,
                            PICKER_FRAGMENT_TAG
                    )
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commitAllowingStateLoss()
        } else {
            fragment.setMediaPickerListener(this)
        }
        requireNotNull(actionBar).setTitle(mediaPickerSetup.title)
    }

    private val pickerFragment: MediaPickerFragment?
        get() {
            val fragment = supportFragmentManager.findFragmentByTag(
                    PICKER_FRAGMENT_TAG
            )
            return if (fragment != null) {
                fragment as MediaPickerFragment
            } else null
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaPickerSetup.toBundle(outState)
        if (!TextUtils.isEmpty(mediaCapturePath)) {
            outState.putString(KEY_MEDIA_CAPTURE_PATH, mediaCapturePath)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mediaCapturePath = savedInstanceState.getString(KEY_MEDIA_CAPTURE_PATH)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        val intent: Intent? = when (requestCode) {
            MEDIA_LIBRARY -> {
                data?.let {
                    val uris = WPMediaUtils.retrieveMediaUris(data)
                    pickerFragment?.urisSelectedFromSystemPicker(uris)
                    return
                }
            }
            TAKE_PHOTO -> {
                try {
                    val intent = Intent()
                    mediaCapturePath!!.let {
                        WPMediaUtils.scanMediaFile(this, it)
                        val f = File(it)
                        val capturedImageUri = listOf(Uri.fromFile(f))
                        if (mediaPickerSetup.queueResults) {
                            intent.putQueuedUris(capturedImageUri)
                        } else {
                            intent.putUris(capturedImageUri)
                        }
                        intent.putExtra(
                                EXTRA_MEDIA_SOURCE,
                                ANDROID_CAMERA.name
                        )
                    }
                    intent
                } catch (e: RuntimeException) {
                    AppLog.e(MEDIA, e)
                    null
                }
            }
            IMAGE_EDITOR_EDIT_IMAGE -> {
                data?.let {
                    val intent = Intent()
                    // TODO: 20/07/2021 There's a whole ImageEditor module in WPAndroid. Should we import it?
                    val uris = WPMediaUtils.retrieveImageEditorResult(data)
                    if (mediaPickerSetup.queueResults) {
                        intent.putQueuedUris(uris)
                    } else {
                        intent.putUris(uris)
                    }
                    intent.putExtra(
                            EXTRA_MEDIA_SOURCE,
                            APP_PICKER.name
                    )
                    intent
                }
            }
            else -> {
                data
            }
        }
        intent?.let {
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun launchChooserWithContext(openSystemPicker: OpenSystemPicker) {
        WPMediaUtils.launchChooserWithContext(this, openSystemPicker, MEDIA_LIBRARY)
    }

    private fun launchWPStoriesCamera() {
        val intent = Intent()
                .putExtra(EXTRA_LAUNCH_WPSTORIES_CAMERA_REQUESTED, true)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun Intent.putUris(
        mediaUris: List<Uri>
    ) {
        this.putExtra(EXTRA_MEDIA_URIS, mediaUris.toStringArray())
    }

    private fun Intent.putQueuedUris(
        mediaUris: List<Uri>
    ) {
        this.putExtra(EXTRA_MEDIA_QUEUED_URIS, mediaUris.toStringArray())
    }

    private fun Intent.putMediaIds(
        mediaIds: List<Long>
    ) {
        this.putExtra(RESULT_IDS, mediaIds.toLongArray())
        this.putExtra(EXTRA_MEDIA_ID, mediaIds[0])
    }

    private fun Intent.putLocalIds(
        mediaLocalIds: List<Int>
    ) {
        this.putExtra(
                MediaPickerConstants.EXTRA_SAVED_MEDIA_MODEL_LOCAL_IDS,
                mediaLocalIds.toIntArray()
        )
    }

    override fun onItemsChosen(identifiers: List<Identifier>) {
        val chosenLocalUris = identifiers.mapNotNull { (it as? Identifier.LocalUri) }
        val chosenUris = chosenLocalUris.filter { !it.queued }.map { it.value.uri }
        val queuedUris = chosenLocalUris.filter { it.queued }.map { it.value.uri }
        val chosenIds = identifiers.mapNotNull { (it as? Identifier.RemoteId)?.value }
        val chosenLocalIds = identifiers.mapNotNull { (it as? Identifier.LocalId)?.value }

        val intent = Intent()
        if (!chosenUris.isNullOrEmpty()) {
            intent.putUris(chosenUris)
        }
        if (!queuedUris.isNullOrEmpty()) {
            intent.putQueuedUris(queuedUris)
        }
        if (!chosenIds.isNullOrEmpty()) {
            intent.putMediaIds(chosenIds)
        }
        if (!chosenLocalIds.isNullOrEmpty()) {
            intent.putLocalIds(chosenLocalIds)
        }
        val source = MediaPickerMediaSource.fromDataSource(mediaPickerSetup.primaryDataSource)
        intent.putExtra(
                EXTRA_MEDIA_SOURCE,
                source.name
        )
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onIconClicked(action: MediaPickerAction) {
        when (action) {
            is OpenSystemPicker -> {
                launchChooserWithContext(action)
            }
            is OpenCameraForWPStories -> launchWPStoriesCamera()
            is SwitchMediaPicker -> {
                startActivityForResult(buildIntent(this, action.mediaPickerSetup), PHOTO_PICKER)
            }
            OpenCameraForPhotos -> {
                WPMediaUtils.launchCamera(this, applicationContext.packageName) { mediaCapturePath = it }
            }
        }
    }

    private fun List<Uri>.toStringArray() = this.map { it.toString() }.toTypedArray()

    companion object {
        private const val PICKER_FRAGMENT_TAG = "picker_fragment_tag"
        private const val KEY_MEDIA_CAPTURE_PATH = "media_capture_path"

        fun buildIntent(
            context: Context,
            mediaPickerSetup: MediaPickerSetup
        ): Intent {
            val intent = Intent(context, MediaPickerActivity::class.java)
            mediaPickerSetup.toIntent(intent)
            return intent
        }
    }
}
