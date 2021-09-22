package org.wordpress.android.sampleapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.MediaPickerLauncher

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        findViewById<Button>(R.id.button).setOnClickListener {
            MediaPickerLauncher.showMediaPickerForResult(this, true, false, true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val message = data?.extras?.keySet()?.map {
            data.extras?.get(it)
        }?.joinToString() ?: ""

        Snackbar.make(findViewById<Button>(R.id.content), data.toString(), Snackbar.LENGTH_LONG).show()
    }
}
