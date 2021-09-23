package org.wordpress.android.sampleapp

import android.os.Bundle
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.MediaPickerConstants
import org.wordpress.android.mediapicker.MediaPickerLauncher
import org.wordpress.android.sampleapp.R.id
import org.wordpress.android.sampleapp.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

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

        binding.button.setOnClickListener {
            val mediaPickerIntent = MediaPickerLauncher.buildMediaPickerIntent(
                activity = this,
                isImagePicker = true,
                isVideoPicker = false,
                canMultiSelect = true
            )
            resultLauncher.launch(mediaPickerIntent)
        }
    }

    private fun handleMediaPickerResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val message =
                (result.data?.extras?.get(MediaPickerConstants.EXTRA_MEDIA_URIS) as? Array<*>)?.map {
                    it as? String
                }?.joinToString("\n") ?: ""

            Snackbar.make(findViewById<Button>(id.content), message, Snackbar.LENGTH_LONG).show()
        }
    }
}
