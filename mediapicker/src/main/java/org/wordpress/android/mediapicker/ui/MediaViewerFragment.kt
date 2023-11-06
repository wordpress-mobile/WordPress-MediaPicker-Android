package org.wordpress.android.mediapicker.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.databinding.MediaPickerLibViewerFragmentBinding

/**
 * Fullscreen single image viewer
 */
@AndroidEntryPoint
internal class MediaViewerFragment :
    Fragment(R.layout.media_picker_lib_viewer_fragment),
    RequestListener<Drawable> {
    companion object {
        const val IMAGE_URL_KEY = "image_url_key"
        private const val VIEWER_FRAGMENT_TAG = "viewer_fragment_tag"

        fun previewUrl(activity: FragmentActivity, url: String) {
            val fragment = activity.supportFragmentManager.findFragmentByTag(VIEWER_FRAGMENT_TAG)
                ?: MediaViewerFragment()
            fragment.arguments = Bundle().apply { putString(IMAGE_URL_KEY, url) }
            activity.supportFragmentManager.beginTransaction()
                .add(
                    R.id.fragment_container,
                    fragment,
                    VIEWER_FRAGMENT_TAG
                )
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .addToBackStack("preview")
                .commitAllowingStateLoss()
        }
    }

    private var _binding: MediaPickerLibViewerFragmentBinding? = null
    private val binding get() = _binding!!

    private var imageUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        imageUrl = requireArguments().getString(IMAGE_URL_KEY)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(IMAGE_URL_KEY, imageUrl)
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = MediaPickerLibViewerFragmentBinding.bind(view)

        binding.iconBack.setOnClickListener {
            @Suppress("DEPRECATION")
            activity?.onBackPressed()
        }
        loadImage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onStop() {
        super.onStop()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    private fun loadImage() {
        binding.progressBar.isVisible = true
        Glide.with(this)
            .load(imageUrl)
            .listener(this)
            .into(binding.photoView)
    }

    /**
     * Glide failed to load the image
     */
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Drawable>,
        isFirstResource: Boolean
    ): Boolean {
        binding.progressBar.isVisible = false
        return false
    }

    /**
     * Glide has loaded the image, hide the progress bar
     */
    override fun onResourceReady(
        resource: Drawable,
        model: Any,
        target: Target<Drawable>?,
        dataSource: DataSource,
        isFirstResource: Boolean
    ): Boolean {
        binding.progressBar.isVisible = false
        return false
    }
}
