package org.wordpress.android.mediapicker

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.mediapicker.databinding.FragmentMediaViewerBinding

/**
 * Fullscreen single image viewer
 */
@AndroidEntryPoint
class MediaViewerFragment : Fragment(R.layout.fragment_media_viewer),
    RequestListener<Drawable> {
    companion object {
        const val IMAGE_URL_KEY = "image_url_key"
    }

    private var _binding: FragmentMediaViewerBinding? = null
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

        _binding = FragmentMediaViewerBinding.bind(view)

        binding.iconBack.setOnClickListener {
            activity?.onBackPressed()
        }
        loadImage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        target: com.bumptech.glide.request.target.Target<Drawable>?,
        isFirstResource: Boolean
    ): Boolean {
        binding.progressBar.isVisible = false
        return false
    }

    /**
     * Glide has loaded the image, hide the progress bar
     */
    override fun onResourceReady(
        resource: Drawable?,
        model: Any?,
        target: com.bumptech.glide.request.target.Target<Drawable>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ): Boolean {
        binding.progressBar.isVisible = false
        return false
    }
}
