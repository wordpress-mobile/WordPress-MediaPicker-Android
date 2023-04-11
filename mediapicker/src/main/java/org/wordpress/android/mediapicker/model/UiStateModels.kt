package org.wordpress.android.mediapicker.model

import android.Manifest
import android.Manifest.permission
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.model.UiString.UiStringRes

internal class UiStateModels {
    data class MediaPickerUiState(
        val photoListUiModel: PhotoListUiModel,
        val softAskViewUiModel: SoftAskViewUiModel,
        val fabUiModel: FabUiModel,
        val actionModeUiModel: ActionModeUiModel,
        val searchUiModel: SearchUiModel,
        val isRefreshing: Boolean,
        val browseMenuUiModel: BrowseMenuUiModel
    )

    sealed class PhotoListUiModel {
        data class Data(val items: List<MediaPickerUiItem>) : PhotoListUiModel()

        data class Empty(
            val title: UiString,
            val htmlSubtitle: UiString? = null,
            val image: Int? = null,
            val bottomImage: Int? = null,
            val bottomImageDescription: UiString? = null,
            val isSearching: Boolean = false,
            val retryAction: (() -> Unit)? = null
        ) : PhotoListUiModel()

        object Hidden : PhotoListUiModel()
        object Loading : PhotoListUiModel()
    }

    sealed class SoftAskViewUiModel {
        data class Visible(
            val label: String,
            val allowId: UiStringRes,
            val isAlwaysDenied: Boolean,
            val onClick: () -> Unit
        ) : SoftAskViewUiModel()

        object Hidden : SoftAskViewUiModel()
    }

    data class FabUiModel(val show: Boolean, val action: () -> Unit)

    sealed class ActionModeUiModel {
        data class Visible(
            val actionModeTitle: UiString? = null
        ) : ActionModeUiModel()

        object Hidden : ActionModeUiModel()
    }

    sealed class SearchUiModel {
        object Collapsed : SearchUiModel()
        data class Expanded(val filter: String, val closeable: Boolean = true) : SearchUiModel()
        object Hidden : SearchUiModel()
    }

    data class BrowseMenuUiModel(val shownActions: Set<DataSource>)

    @Parcelize
    data class SoftAskRequest(
        val show: Boolean,
        val permissions: List<PermissionsRequested> = emptyList(),
        val isAlwaysDenied: Boolean = false,
    ) : Parcelable

    enum class PermissionsRequested {
        CAMERA, READ_STORAGE, WRITE_STORAGE, IMAGES, VIDEOS, MUSIC;

        companion object {
            fun fromString(permission: String): PermissionsRequested {
                return when (permission) {
                    Manifest.permission.CAMERA -> CAMERA
                    Manifest.permission.READ_EXTERNAL_STORAGE -> READ_STORAGE
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> WRITE_STORAGE
                    Manifest.permission.READ_MEDIA_IMAGES -> IMAGES
                    Manifest.permission.READ_MEDIA_VIDEO -> VIDEOS
                    Manifest.permission.READ_MEDIA_AUDIO -> MUSIC
                    else -> throw UnsupportedOperationException("Unsupported permission: $permission")
                }
            }

            fun fromMediaType(type: MediaType): PermissionsRequested {
                return when (type) {
                    IMAGE -> IMAGES
                    VIDEO -> VIDEOS
                    AUDIO -> MUSIC
                    else -> throw UnsupportedOperationException("Unsupported media type: $type")
                }
            }
        }

        @Suppress("ExceptionRaisedInUnexpectedLocation")
        override fun toString(): String {
            return when (this) {
                CAMERA -> permission.CAMERA
                READ_STORAGE -> permission.READ_EXTERNAL_STORAGE
                WRITE_STORAGE -> permission.WRITE_EXTERNAL_STORAGE
                IMAGES -> if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    permission.READ_MEDIA_IMAGES
                } else {
                    throw UnsupportedOperationException("Unsupported permission: $this")
                }
                VIDEOS -> if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    permission.READ_MEDIA_VIDEO
                } else {
                    throw UnsupportedOperationException("Unsupported permission: $this")
                }
                MUSIC -> if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    permission.READ_MEDIA_AUDIO
                } else {
                    throw UnsupportedOperationException("Unsupported permission: $this")
                }
            }
        }
    }
}
