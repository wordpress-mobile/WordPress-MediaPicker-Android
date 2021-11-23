package org.wordpress.android.mediapicker.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.STORAGE
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
        val type: PermissionsRequested = STORAGE,
        val isAlwaysDenied: Boolean = false,
    ) : Parcelable

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }
}