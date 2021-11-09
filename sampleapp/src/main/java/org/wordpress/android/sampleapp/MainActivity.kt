package org.wordpress.android.sampleapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.source.device.DeviceMediaPickerSetup
import org.wordpress.android.mediapicker.source.device.DeviceMediaPickerSetup.MediaTypes.IMAGES
import org.wordpress.android.mediapicker.source.device.GifMediaPickerSetup
import org.wordpress.android.mediapicker.ui.MediaPickerActivity
import org.wordpress.android.mediapicker.MediaPickerUtils
import org.wordpress.android.sampleapp.R.id
import org.wordpress.android.sampleapp.databinding.ActivityMainBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var mediaPickerUtils: MediaPickerUtils

    private val resultLauncher = registerForActivityResult(StartActivityForResult()) {
        handleMediaPickerResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onStart() {
        super.onStart()

        binding.devicePickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                DeviceMediaPickerSetup.buildMediaPicker(
                    mediaTypes = IMAGES,
                    canMultiSelect = true
                )
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.systemPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                DeviceMediaPickerSetup.buildSystemPicker(
                    mediaTypes = IMAGES,
                    canMultiSelect = false
                )
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.cameraPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                DeviceMediaPickerSetup.buildCameraPicker()
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.gifPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                GifMediaPickerSetup.build(canMultiSelect = true)
            )
            resultLauncher.launch(mediaPickerIntent)
        }
    }

    private fun handleMediaPickerResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val message =
                (result.data?.extras?.get(MediaPickerConstants.EXTRA_MEDIA_URIS) as? Array<*>)
                    ?.map { mediaPickerUtils.getMediaStoreFilePath(Uri.parse(it as String)) }
                    ?.joinToString("\n") ?: ""

            Snackbar.make(findViewById<Button>(id.content), message, Snackbar.LENGTH_LONG).show()
        }
    }
}
