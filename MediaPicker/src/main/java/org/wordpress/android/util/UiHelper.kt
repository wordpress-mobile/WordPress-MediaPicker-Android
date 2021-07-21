package org.wordpress.android.util

import android.content.Context

object UiHelper {
    fun getTextOfUiString(context: Context, uiString: UiString): CharSequence =
            when (uiString) {
                is UiString.UiStringRes -> context.getString(uiString.stringRes)
                is UiString.UiStringText -> uiString.text
                is UiString.UiStringResWithParams -> context.getString(
                        uiString.stringRes,
                        *uiString.params.map { value ->
                            getTextOfUiString(
                                    context,
                                    value
                            )
                        }.toTypedArray()
                )
            }
}