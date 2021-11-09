package org.wordpress.android.mediapicker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.databinding.MediaPickerLibActivityBinding
import org.wordpress.android.mediapicker.util.Log
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerActivity : AppCompatActivity() {
    @Inject lateinit var log: Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MediaPickerLibActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarMain.setNavigationIcon(drawable.ic_close_white_24dp)
        setSupportActionBar(binding.toolbarMain)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
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
