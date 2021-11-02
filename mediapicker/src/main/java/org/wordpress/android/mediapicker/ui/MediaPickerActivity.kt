package org.wordpress.android.mediapicker.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.provider.MediaStore.MediaColumns
import android.text.TextUtils
import android.view.MenuItem
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.mediapicker.MediaManager
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_ID
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_QUEUED_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_SOURCE
import org.wordpress.android.mediapicker.MediaPickerConstants.EXTRA_MEDIA_URIS
import org.wordpress.android.mediapicker.MediaPickerConstants.RESULT_IDS
import org.wordpress.android.mediapicker.ui.MediaPickerActivity.MediaPickerMediaSource.ANDROID_CAMERA
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.Companion.newInstance
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerListener
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.MEDIA_LIBRARY
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.PHOTO_PICKER
import org.wordpress.android.mediapicker.MediaPickerRequestCodes.TAKE_PHOTO
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.R.id
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.databinding.MediaPickerLibActivityBinding
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.Log
import org.wordpress.android.mediapicker.util.asAndroidUri
import org.wordpress.android.mediapicker.util.asMediaUri
import org.wordpress.android.mediapicker.util.MediaUtils
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerActivity : AppCompatActivity(), MediaPickerListener {
    private var mediaCapturePath: String? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    @Inject lateinit var log: Log
    @Inject lateinit var mediaManager: MediaManager

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
        lifecycleScope.launch {
            val intent: Intent? = when (requestCode) {
                MEDIA_LIBRARY -> {
                    if (resultCode != Activity.RESULT_OK) {
                        setResult(Activity.RESULT_CANCELED, intent)
                        if (mediaPickerSetup.primaryDataSource == SYSTEM_PICKER) {
                            finish()
                        }
                        return@launch
                    } else {
                        data?.let {
                            val uris = MediaUtils.retrieveMediaUris(data)
                            pickerFragment?.urisSelectedFromSystemPicker(uris)
                            return@launch
                        }
                    }
                }
                TAKE_PHOTO -> {
                    if (resultCode != Activity.RESULT_OK) {
                        setResult(Activity.RESULT_CANCELED, intent)
                        if (mediaPickerSetup.primaryDataSource == CAMERA) {
                            finish()
                        }
                        return@launch
                    } else {
                        try {
                            val intent = Intent()
                            mediaCapturePath!!.let {
                                mediaManager.saveImage(it)?.let { uri ->
                                    val capturedImageUri = listOf(uri.asMediaUri())
                                    if (mediaPickerSetup.areResultsQueued) {
                                        intent.putQueuedUris(capturedImageUri)
                                    } else {
                                        intent.putUris(capturedImageUri)
                                    }
                                    intent.putExtra(
                                        EXTRA_MEDIA_SOURCE,
                                        ANDROID_CAMERA.name
                                    )
                                }
                            }
                            intent
                        } catch (e: RuntimeException) {
                            log.e(e)
                            finish()
                            null
                        }
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
    }

    private fun launchChooserWithContext(openSystemPicker: OpenSystemPicker) {
        MediaUtils.launchChooserWithContext(this, openSystemPicker, MEDIA_LIBRARY)
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

    override fun onIconClicked(action: MediaPickerAction) {
        when (action) {
            is OpenSystemPicker -> {
                launchChooserWithContext(action)
            }
            is SwitchMediaPicker -> {
                startActivityForResult(buildIntent(this, action.mediaPickerSetup), PHOTO_PICKER)
            }
            OpenCameraForPhotos -> {
                MediaUtils.launchCamera(log,this, applicationContext.packageName) {
                    mediaCapturePath = it
                }
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
