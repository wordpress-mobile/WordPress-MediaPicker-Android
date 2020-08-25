package org.wordpress.android.mediapicker

import android.Manifest.permission
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_PREVIEW_OPENED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MEDIA_PICKER_RECENT_MEDIA_SELECTED
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.media.MediaBrowserType
import org.wordpress.android.ui.photopicker.PermissionsHandler
import org.wordpress.android.mediapicker.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon.WP_STORIES_CAPTURE
import org.wordpress.android.mediapicker.MediaPickerUiItem.ClickAction
import org.wordpress.android.mediapicker.MediaPickerUiItem.ToggleAction
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.MediaUtils
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.util.ViewWrapper
import org.wordpress.android.util.WPPermissionUtils
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.analytics.AnalyticsUtilsWrapper
import org.wordpress.android.util.distinct
import org.wordpress.android.util.merge
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ResourceProvider
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

class MediaPickerViewModel @Inject constructor(
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val mediaLoaderFactory: MediaLoaderFactory,
    private val analyticsUtilsWrapper: AnalyticsUtilsWrapper,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val permissionsHandler: PermissionsHandler,
    private val context: Context,
    private val resourceProvider: ResourceProvider
) : ScopedViewModel(mainDispatcher) {
    private lateinit var mediaLoader: MediaLoader
    private val loadActions = Channel<LoadAction>()
    private val _navigateToPreview = MutableLiveData<Event<UriWrapper>>()
    private val _navigateToEdit = MutableLiveData<Event<List<UriWrapper>>>()
    private val _onInsert = MutableLiveData<Event<List<UriWrapper>>>()
    private val _showPopupMenu = MutableLiveData<Event<PopupMenuUiModel>>()
    private val _photoPickerItems = MutableLiveData<List<MediaItem>>()
    private val _selectedIds = MutableLiveData<List<Long>>()
    private val _onIconClicked = MutableLiveData<Event<IconClickEvent>>()
    private val _onPermissionsRequested = MutableLiveData<Event<PermissionsRequested>>()
    private val _softAskRequest = MutableLiveData<SoftAskRequest>()

    val onNavigateToPreview: LiveData<Event<UriWrapper>> = _navigateToPreview
    val onNavigateToEdit: LiveData<Event<List<UriWrapper>>> = _navigateToEdit
    val onInsert: LiveData<Event<List<UriWrapper>>> = _onInsert
    val onIconClicked: LiveData<Event<IconClickEvent>> = _onIconClicked

    val onShowPopupMenu: LiveData<Event<PopupMenuUiModel>> = _showPopupMenu
    val onPermissionsRequested: LiveData<Event<PermissionsRequested>> = _onPermissionsRequested

    val selectedIds: LiveData<List<Long>> = _selectedIds

    val uiState: LiveData<MediaPickerUiState> = merge(
            _photoPickerItems.distinct(),
            _selectedIds.distinct(),
            _softAskRequest
    ) { photoPickerItems, selectedIds, softAskRequest ->
        MediaPickerUiState(
                buildUiModel(photoPickerItems, selectedIds),
                buildSoftAskView(softAskRequest),
                FabUiModel(browserType.isWPStoriesPicker) {
                    clickIcon(WP_STORIES_CAPTURE)
                },
                buildActionModeUiModel(selectedIds, photoPickerItems)
        )
    }

    var lastTappedIcon: MediaPickerIcon? = null
    private lateinit var browserType: MediaBrowserType
    private var site: SiteModel? = null

    private fun buildUiModel(
        data: List<MediaItem>?,
        selectedIds: List<Long>?
    ): PhotoListUiModel {
        return if (data != null) {
            val uiItems = data.map {
                val showOrderCounter = browserType.canMultiselect()
                val toggleAction = ToggleAction(it.id, showOrderCounter, this::toggleItem)
                val clickAction = ClickAction(it.id, it.uri, it.isVideo, this::clickItem)
                val (selectedOrder, isSelected) = if (selectedIds != null && selectedIds.contains(it.id)) {
                    val selectedOrder = if (showOrderCounter) selectedIds.indexOf(it.id) + 1 else null
                    val isSelected = true
                    selectedOrder to isSelected
                } else {
                    null to false
                }
                if (it.isVideo) {
                    MediaPickerUiItem.VideoItem(
                            id = it.id,
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                } else {
                    MediaPickerUiItem.PhotoItem(
                            id = it.id,
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                }
            }
            PhotoListUiModel.Data(uiItems)
        } else {
            PhotoListUiModel.Empty
        }
    }

    private fun buildActionModeUiModel(
        selectedIds: List<Long>?,
        items: List<MediaItem>?
    ): ActionModeUiModel {
        val numSelected = selectedIds?.size ?: 0
        if (selectedIds.isNullOrEmpty()) {
            return ActionModeUiModel.Hidden
        }
        val title: UiString? = when {
            numSelected == 0 -> null
            browserType.canMultiselect() -> {
                UiStringText(String.format(resourceProvider.getString(R.string.cab_selected), numSelected))
            }
            else -> {
                if (browserType.isImagePicker && browserType.isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_media)
                } else if (browserType.isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_video)
                } else {
                    UiStringRes(R.string.photo_picker_use_photo)
                }
            }
        }
        val isVideoSelected = items?.any { it.isVideo && selectedIds.contains(it.id) } ?: false
        return ActionModeUiModel.Visible(
                title,
                showEditAction = browserType.isGutenbergPicker && !isVideoSelected
        )
    }

    fun refreshData(forceReload: Boolean) {
        if (!permissionsHandler.hasStoragePermission()) {
            return
        }
        launch(bgDispatcher) {
            loadActions.send(LoadAction.Refresh)
        }
    }

    fun clearSelection() {
        if (!_selectedIds.value.isNullOrEmpty()) {
            _selectedIds.postValue(listOf())
        }
    }

    fun start(
        selectedIds: List<Long>?,
        browserType: MediaBrowserType,
        lastTappedIcon: MediaPickerIcon?,
        site: SiteModel?
    ) {
        this.mediaLoader = mediaLoaderFactory.build()
        selectedIds?.let {
            _selectedIds.value = selectedIds
        }
        this.browserType = browserType
        this.lastTappedIcon = lastTappedIcon
        this.site = site
        launch(bgDispatcher) {
            mediaLoader.loadMedia(loadActions).collect { domainModel ->
                withContext(mainDispatcher) {
                    _photoPickerItems.value = domainModel.domainItems
                }
            }
        }
        launch(bgDispatcher) {
            val mediaTypes = mutableSetOf<MediaType>()
            if (browserType.isVideoPicker) {
                mediaTypes.add(VIDEO)
            }
            if (browserType.isImagePicker) {
                mediaTypes.add(IMAGE)
            }
            loadActions.send(LoadAction.Start(mediaTypes))
        }
    }

    fun numSelected(): Int {
        return _selectedIds.value?.size ?: 0
    }

    fun selectedURIs(): List<UriWrapper> {
        val items = (uiState.value?.photoListUiModel as? PhotoListUiModel.Data)?.items
        return _selectedIds.value?.mapNotNull { id -> items?.find { it.id == id }?.uri } ?: listOf()
    }

    private fun toggleItem(id: Long, canMultiselect: Boolean) {
        val updatedIds = _selectedIds.value?.toMutableList() ?: mutableListOf()
        if (updatedIds.contains(id)) {
            updatedIds.remove(id)
        } else {
            if (updatedIds.isNotEmpty() && !canMultiselect) {
                updatedIds.clear()
            }
            updatedIds.add(id)
        }
        _selectedIds.postValue(updatedIds)
    }

    private fun clickItem(id: Long, uri: UriWrapper?, isVideo: Boolean) {
        trackOpenPreviewScreenEvent(id, uri, isVideo)
        uri?.let {
            _navigateToPreview.postValue(Event(it))
        }
    }

    private fun trackOpenPreviewScreenEvent(id: Long, uri: UriWrapper?, isVideo: Boolean) {
        launch(bgDispatcher) {
            val properties = analyticsUtilsWrapper.getMediaProperties(
                    isVideo,
                    uri,
                    null
            )
            properties["is_video"] = isVideo
            analyticsTrackerWrapper.track(MEDIA_PICKER_PREVIEW_OPENED, properties)
        }
    }

    fun performInsertAction() {
        val uriList = selectedURIs()
        _onInsert.value = Event(uriList)
        val isMultiselection = uriList.size > 1
        for (mediaUri in uriList) {
            val isVideo = MediaUtils.isVideo(mediaUri.toString())
            val properties = analyticsUtilsWrapper.getMediaProperties(
                    isVideo,
                    mediaUri,
                    null
            )
            properties["is_part_of_multiselection"] = isMultiselection
            if (isMultiselection) {
                properties["number_of_media_selected"] = uriList.size
            }
            analyticsTrackerWrapper.track(MEDIA_PICKER_RECENT_MEDIA_SELECTED, properties)
        }
    }

    fun performEditAction() {
        val uriList = selectedURIs()
        _navigateToEdit.value = Event(uriList)
    }

    fun clickOnLastTappedIcon() = clickIcon(lastTappedIcon!!)

    private fun clickIcon(icon: MediaPickerIcon) {
        if (icon == WP_STORIES_CAPTURE) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onPermissionsRequested.value = Event(PermissionsRequested.CAMERA)
                lastTappedIcon = icon
                return
            }
            AnalyticsTracker.track(MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE)
        }
        _onIconClicked.postValue(Event(IconClickEvent(icon, browserType.canMultiselect())))
    }

    fun checkStoragePermission(isAlwaysDenied: Boolean) {
        if (permissionsHandler.hasStoragePermission()) {
            _softAskRequest.value = SoftAskRequest(show = false, isAlwaysDenied = isAlwaysDenied)
            if (_photoPickerItems.value.isNullOrEmpty()) {
                refreshData(false)
            }
        } else {
            _softAskRequest.value = SoftAskRequest(show = true, isAlwaysDenied = isAlwaysDenied)
        }
    }

    private fun buildSoftAskView(softAskRequest: SoftAskRequest?): SoftAskViewUiModel {
        if (softAskRequest != null && softAskRequest.show) {
            val appName = "<strong>${resourceProvider.getString(R.string.app_name)}</strong>"
            val label = if (softAskRequest.isAlwaysDenied) {
                val permissionName = ("<strong>${WPPermissionUtils.getPermissionName(
                        context,
                        permission.WRITE_EXTERNAL_STORAGE
                )}</strong>")
                String.format(
                        resourceProvider.getString(R.string.photo_picker_soft_ask_permissions_denied), appName,
                        permissionName
                )
            } else {
                String.format(
                        resourceProvider.getString(R.string.photo_picker_soft_ask_label),
                        appName
                )
            }
            val allowId = if (softAskRequest.isAlwaysDenied) {
                R.string.button_edit_permissions
            } else {
                R.string.photo_picker_soft_ask_allow
            }
            return SoftAskViewUiModel.Visible(label, UiStringRes(allowId), softAskRequest.isAlwaysDenied)
        } else {
            return SoftAskViewUiModel.Hidden
        }
    }

    data class MediaPickerUiState(
        val photoListUiModel: PhotoListUiModel,
        val softAskViewUiModel: SoftAskViewUiModel,
        val fabUiModel: FabUiModel,
        val actionModeUiModel: ActionModeUiModel
    )

    sealed class PhotoListUiModel {
        data class Data(val items: List<MediaPickerUiItem>) :
                PhotoListUiModel()

        object Empty : PhotoListUiModel()
    }

    sealed class SoftAskViewUiModel {
        data class Visible(val label: String, val allowId: UiStringRes, val isAlwaysDenied: Boolean) :
                SoftAskViewUiModel()

        object Hidden : SoftAskViewUiModel()
    }

    data class FabUiModel(val show: Boolean, val action: () -> Unit)

    sealed class ActionModeUiModel {
        data class Visible(
            val actionModeTitle: UiString? = null,
            val showEditAction: Boolean = false
        ) : ActionModeUiModel()

        object Hidden : ActionModeUiModel()
    }

    data class IconClickEvent(val icon: MediaPickerIcon, val allowMultipleSelection: Boolean)

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    data class PopupMenuUiModel(val view: ViewWrapper, val items: List<PopupMenuItem>) {
        data class PopupMenuItem(val title: UiStringRes, val action: () -> Unit)
    }

    data class SoftAskRequest(val show: Boolean, val isAlwaysDenied: Boolean)
}
