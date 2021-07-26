package org.wordpress.android.mediapicker.api

interface MimeTypeSupportProvider {

    fun isMimeTypeSupportedBySitePlan(siteId: Long, mimeType: String): Boolean

    fun isSupportedApplicationType(mimeType: String): Boolean

    fun isSupportedMimeType(mimeType: String): Boolean

    fun getMimeTypeForExtension(fileExtension: String): String
}