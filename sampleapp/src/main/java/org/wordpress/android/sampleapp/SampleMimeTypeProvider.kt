package org.wordpress.android.sampleapp

import org.wordpress.android.mediapicker.api.MimeTypeProvider
import javax.inject.Inject

class SampleMimeTypeProvider @Inject constructor() : MimeTypeProvider {
    override val imageTypes: List<String> = listOf("image/jpeg", "image/png")
    override val videoTypes: List<String> = emptyList()
    override val audioTypes: List<String> = emptyList()
    override val documentTypes: List<String> = listOf("application/pdf")

    override fun isApplicationTypeSupported(applicationType: String): Boolean = true
    override fun isMimeTypeSupported(mimeType: String): Boolean = true
}
