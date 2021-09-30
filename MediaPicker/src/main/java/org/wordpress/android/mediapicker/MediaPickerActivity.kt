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
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.IMAGE_EDITOR_EDIT_IMAGE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_ID
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_QUEUED_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_SOURCE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.RESULT_IDS
import org.wordpress.android.mediapicker.MediaPickerActivity.MediaPickerMediaSource.ANDROID_CAMERA
import org.wordpress.android.mediapicker.MediaPickerActivity.MediaPickerMediaSource.APP_PICKER
import org.wordpress.android.mediapicker.MediaPickerFragment.Companion.newInstance
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerListener
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.MEDIA_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.PHOTO_PICKER
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.TAKE_PHOTO
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.databinding.MediaPickerActivityBinding
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.Log
import org.wordpress.android.mediapicker.util.asAndroidUri
import org.wordpress.android.mediapicker.util.asMediaUri
import org.wordpress.android.util.WPMediaUtils
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerActivity : AppCompatActivity(), MediaPickerListener {
    private var mediaCapturePath: String? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    @Inject lateinit var log: Log

    enum class MediaPickerMediaSource {
        ANDROID_CAMERA, APP_PICKER;

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
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MediaPickerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarMain.setNavigationIcon(R.drawable.ic_close_white_24dp)
        setSupportActionBar(binding.toolbarMain)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowTitleEnabled(true)
        }
        mediaPickerSetup = if (savedInstanceState == null) {
            MediaPickerSetup.fromIntent(intent)
        } else {
            MediaPickerSetup.fromBundle(savedInstanceState)
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
                        WPMediaUtils.scanMediaFile(log,this, it)
                        val f = File(it)
                        val capturedImageUri = listOf(Uri.fromFile(f).asMediaUri())
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
                    log.e(e)
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

    private fun Intent.putUris(
        mediaUris: List<MediaUri>
    ) {
        this.putExtra(EXTRA_MEDIA_URIS, mediaUris.map(MediaUri::asAndroidUri).toStringArray())
    }

    private fun Intent.putQueuedUris(
        mediaUris: List<MediaUri>
    ) {
        this.putExtra(EXTRA_MEDIA_QUEUED_URIS, mediaUris.map(MediaUri::asAndroidUri).toStringArray())
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
        val chosenUris = chosenLocalUris.filter { !it.queued }.map { it.value }
        val queuedUris = chosenLocalUris.filter { it.queued }.map { it.value }
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
            is SwitchMediaPicker -> {
                startActivityForResult(buildIntent(this, action.mediaPickerSetup), PHOTO_PICKER)
            }
            OpenCameraForPhotos -> {
                WPMediaUtils.launchCamera(log,this, applicationContext.packageName) { mediaCapturePath = it }
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
