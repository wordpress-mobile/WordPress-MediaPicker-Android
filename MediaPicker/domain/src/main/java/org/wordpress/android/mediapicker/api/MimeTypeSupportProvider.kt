package org.wordpress.android.mediapicker.api

interface MimeTypeSupportProvider {

    fun isMimeTypeSupportedBySitePlan(siteId: Long, mimeType: String): Boolean

    fun isSupportedApplicationType(mimeType: String): Boolean

    fun isSupportedMimeType(mimeType: String): Boolean

    fun getMimeTypeForExtension(fileExtension: String): String

    fun getExtensionForMimeType(mimeType: String): String

    fun getImageTypesOnly(): List<String>

    fun getVideoTypesOnly(): List<String>

    fun getAudioTypesOnly(): List<String>

    fun getVideoAndImagesTypes(): List<String>

    fun getAllTypes(): List<String>
}