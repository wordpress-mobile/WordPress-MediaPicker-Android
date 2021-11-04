package org.wordpress.android.mediapicker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.wordpress.android.mediapicker.MediaManager
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_ID
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_QUEUED_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_SOURCE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.RESULT_IDS
import org.wordpress.android.mediapicker.ui.MediaPickerActivity.MediaPickerMediaSource.ANDROID_CAMERA
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.Companion.newInstance
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.model.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerListener
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.MEDIA_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.PHOTO_PICKER
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.R.id
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.databinding.MediaPickerLibActivityBinding
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaPickerAction
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerActivity : AppCompatActivity(), MediaPickerListener {
    private lateinit var mediaPickerSetup: MediaPickerSetup
    private var capturedPhotoPath: String? = null

    @Inject lateinit var log: Log
    @Inject lateinit var mediaManager: MediaManager
    @Inject lateinit var mediaPickerUtils: MediaPickerUtils

    enum class MediaPickerMediaSource {
        ANDROID_CAMERA, APP_PICKER, GIF, ANDROID_PICKER;

        companion object {
            fun fromDataSource(dataSource: DataSource): MediaPickerMediaSource {
                return when (dataSource) {
                    DEVICE -> APP_PICKER
                    GIF_LIBRARY -> GIF
                    CAMERA -> ANDROID_CAMERA
                    SYSTEM_PICKER -> ANDROID_PICKER
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MediaPickerLibActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarMain.setNavigationIcon(drawable.ic_close_white_24dp)
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

        savedInstanceState?.let { bundle ->
            capturedPhotoPath = bundle.getString(KEY_CAPTURED_PHOTO_PATH)
        }

        var fragment = pickerFragment
        if (fragment == null) {
            fragment = newInstance(this, mediaPickerSetup)
            supportFragmentManager.beginTransaction()
                    .replace(
                        id.fragment_container,
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
        outState.putString(KEY_CAPTURED_PHOTO_PATH, capturedPhotoPath)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
        val chosenGifUris = identifiers.mapNotNull { (it as? Identifier.GifMediaId) }
        val chosenUris = chosenLocalUris.filter { !it.queued }.map { it.uri }
        val queuedUris = chosenLocalUris.filter { it.queued }.map { it.uri }
        val chosenIds = identifiers.mapNotNull { (it as? Identifier.RemoteId)?.value }
        val chosenLocalIds = identifiers.mapNotNull { (it as? Identifier.LocalId)?.value }

        val intent = Intent()
        if (!chosenUris.isNullOrEmpty()) {
            intent.putUris(chosenUris)
        } else if (!chosenGifUris.isNullOrEmpty()) {
            intent.putUris(chosenGifUris.map { it.uri })
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

    private val systemPicker = registerForActivityResult(StartActivityForResult()) {
        handleSystemPickerResult(it)
    }

    private fun handleSystemPickerResult(result: ActivityResult) {
        lifecycleScope.launch {
            val resultIntent = Intent()
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    val uris = MediaUtils.retrieveMediaUris(it)
                    pickerFragment?.urisSelectedFromSystemPicker(uris)
                    return@launch
                }
            }
            setResult(result.resultCode, resultIntent)
            finish()
        }
    }

    private val camera = registerForActivityResult(StartActivityForResult()) {
        handleImageCaptureResult(it)
    }

    private fun handleImageCaptureResult(result: ActivityResult) {
        lifecycleScope.launch {
            val resultIntent = Intent()
            if (result.resultCode == Activity.RESULT_OK) {
                capturedPhotoPath?.let {
                    val mediaStoreUri = mediaManager.addImageToMediaStore(it)
                    if (mediaStoreUri != null) {
                        val capturedImageUri = listOf(mediaStoreUri.asMediaUri())
                        if (mediaPickerSetup.areResultsQueued) {
                            resultIntent.putQueuedUris(capturedImageUri)
                        } else {
                            resultIntent.putUris(capturedImageUri)
                        }
                        resultIntent.putExtra(
                            EXTRA_MEDIA_SOURCE,
                            ANDROID_CAMERA.name
                        )
                    }
                }
            }
            setResult(result.resultCode, resultIntent)
            finish()
        }
    }

    override fun onIconClicked(action: MediaPickerAction) {
        when (action) {
            is OpenSystemPicker -> {
                val systemPickerIntent = mediaPickerUtils.createSystemPickerIntent(action)
                systemPicker.launch(systemPickerIntent)
            }
            is SwitchMediaPicker -> {
                startActivityForResult(buildIntent(this, action.mediaPickerSetup), PHOTO_PICKER)
            }
            OpenCameraForPhotos -> {
                mediaPickerUtils.createCapturedImageFile(this)?.let {
                    capturedPhotoPath = it.path
                    camera.launch(mediaPickerUtils.createCaptureImageIntent(this, it))
                }
            }
        }
    }

    private fun List<Uri>.toStringArray() = this.map { it.toString() }.toTypedArray()

    companion object {
        private const val PICKER_FRAGMENT_TAG = "picker_fragment_tag"
        private const val KEY_CAPTURED_PHOTO_PATH = "key_captured_photo_uri"

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
