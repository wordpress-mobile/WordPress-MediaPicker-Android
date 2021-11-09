package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MimeTypeProvider
import javax.inject.Inject

class SampleMimeTypeProvider @Inject constructor() : MimeTypeProvider {
    override val imageTypes: List<String> = listOf("JPEG", "PNG")
    override val videoTypes: List<String> = emptyList()
    override val audioTypes: List<String> = emptyList()

    override fun isApplicationTypeSupported(applicationType: String): Boolean = true
    override fun isMimeTypeSupported(mimeType: String): Boolean = true
}
