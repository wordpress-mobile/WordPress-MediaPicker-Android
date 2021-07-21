package org.wordpress.android.mediapicker.model

import android.os.Parcelable

sealed class EditImageData : Parcelable {
    @Parcelize
    data class InputData(
        val highResImgUrl: String,
        val lowResImgUrl: String?,
        val outputFileExtension: String?
    ) : EditImageData()

    @Parcelize
    data class OutputData(val outputFilePath: String) : EditImageData()
}