package org.wordpress.android.mediapicker.util

import android.content.Context
import org.wordpress.android.mediapicker.model.UiString
import org.wordpress.android.mediapicker.model.UiString.UiStringRes
import org.wordpress.android.mediapicker.model.UiString.UiStringText

internal class UiHelpers private constructor() {
    companion object {
        fun getTextOfUiString(context: Context, uiString: UiString): String =
            when (uiString) {
                is UiStringRes -> context.getString(uiString.stringRes)
                is UiStringText -> uiString.text
            }
    }
}
