package org.wordpress.android.mediapicker.api

interface MimeTypeProvider {
    val imageTypes: List<String>
    val videoTypes: List<String>
    val audioTypes: List<String>
    val documentTypes: List<String>

    fun isApplicationTypeSupported(applicationType: String): Boolean
    fun isMimeTypeSupported(mimeType: String): Boolean
}
