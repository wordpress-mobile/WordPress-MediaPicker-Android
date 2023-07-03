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
import org.wordpress.android.mediapicker.MediaPickerUtils
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.CAMERA
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.ui.MediaPickerActivity
import org.wordpress.android.sampleapp.databinding.ActivityMainBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var mediaPickerUtils: MediaPickerUtils

    @Inject
    lateinit var mediaPickerSetupFactory: MediaPickerSetup.Factory

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
                mediaPickerSetupFactory.build(DEVICE)
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.systemPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                mediaPickerSetupFactory.build(SYSTEM_PICKER)
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.cameraPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                mediaPickerSetupFactory.build(CAMERA)
            )
            resultLauncher.launch(mediaPickerIntent)
        }

        binding.gifPickerButton.setOnClickListener {
            val mediaPickerIntent = MediaPickerActivity.buildIntent(
                context = this,
                mediaPickerSetupFactory.build(GIF_LIBRARY)
            )
            resultLauncher.launch(mediaPickerIntent)
        }
    }

    private fun handleMediaPickerResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.extras?.let { extra ->
                val files = (extra.getStringArray(MediaPickerConstants.EXTRA_MEDIA_URIS))
                    ?.map { mediaPickerUtils.getFilePath(Uri.parse(it)) }
                    ?.joinToString("\n") ?: ""

                Snackbar.make(findViewById<Button>(R.id.content), files, Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
