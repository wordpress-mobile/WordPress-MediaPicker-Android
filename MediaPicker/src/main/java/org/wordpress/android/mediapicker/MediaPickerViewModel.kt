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
import org.wordpress.android.ui.photopicker.PermissionsHandler
import org.wordpress.android.mediapicker.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon.WP_STORIES_CAPTURE
import org.wordpress.android.mediapicker.MediaPickerUiItem.ClickAction
import org.wordpress.android.mediapicker.MediaPickerUiItem.ToggleAction
import org.wordpress.android.mediapicker.MediaType.AUDIO
import org.wordpress.android.mediapicker.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.LocaleManagerWrapper
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
    private val localeManagerWrapper: LocaleManagerWrapper,
    private val resourceProvider: ResourceProvider
) : ScopedViewModel(mainDispatcher) {
    private lateinit var mediaLoader: MediaLoader
    private val loadActions = Channel<LoadAction>()
    private val _navigateToPreview = MutableLiveData<Event<UriWrapper>>()
    private val _navigateToEdit = MutableLiveData<Event<List<UriWrapper>>>()
    private val _onInsert = MutableLiveData<Event<List<UriWrapper>>>()
    private val _showPopupMenu = MutableLiveData<Event<PopupMenuUiModel>>()
    private val _photoPickerItems = MutableLiveData<List<MediaItem>>()
    private val _selectedUris = MutableLiveData<List<UriWrapper>>()
    private val _onIconClicked = MutableLiveData<Event<IconClickEvent>>()
    private val _onPermissionsRequested = MutableLiveData<Event<PermissionsRequested>>()
    private val _softAskRequest = MutableLiveData<SoftAskRequest>()

    val onNavigateToPreview: LiveData<Event<UriWrapper>> = _navigateToPreview
    val onNavigateToEdit: LiveData<Event<List<UriWrapper>>> = _navigateToEdit
    val onInsert: LiveData<Event<List<UriWrapper>>> = _onInsert
    val onIconClicked: LiveData<Event<IconClickEvent>> = _onIconClicked

    val onShowPopupMenu: LiveData<Event<PopupMenuUiModel>> = _showPopupMenu
    val onPermissionsRequested: LiveData<Event<PermissionsRequested>> = _onPermissionsRequested

    val selectedUris: LiveData<List<UriWrapper>> = _selectedUris

    val uiState: LiveData<MediaPickerUiState> = merge(
            _photoPickerItems.distinct(),
            _selectedUris.distinct(),
            _softAskRequest
    ) { photoPickerItems, selectedUris, softAskRequest ->
        MediaPickerUiState(
                buildUiModel(photoPickerItems, selectedUris),
                buildSoftAskView(softAskRequest),
                FabUiModel(mediaPickerSetup.cameraEnabled) {
                    clickIcon(WP_STORIES_CAPTURE)
                },
                buildActionModeUiModel(selectedUris, photoPickerItems)
        )
    }

    var lastTappedIcon: MediaPickerIcon? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup
    private var site: SiteModel? = null

    private fun buildUiModel(
        data: List<MediaItem>?,
        selectedUris: List<UriWrapper>?
    ): PhotoListUiModel {
        return if (data != null) {
            val uiItems = data.map {
                val showOrderCounter = mediaPickerSetup.canMultiselect
                val toggleAction = ToggleAction(it.uri, showOrderCounter, this::toggleItem)
                val clickAction = ClickAction(it.uri, it.type == VIDEO, this::clickItem)
                val (selectedOrder, isSelected) = if (selectedUris != null && selectedUris.contains(it.uri)) {
                    val selectedOrder = if (showOrderCounter) selectedUris.indexOf(it.uri) + 1 else null
                    val isSelected = true
                    selectedOrder to isSelected
                } else {
                    null to false
                }

                val fileExtension = it.mimeType?.let { mimeType ->
                    MediaUtils.getExtensionForMimeType(mimeType).toUpperCase(localeManagerWrapper.getLocale())
                }
                when (it.type) {
                    IMAGE -> MediaPickerUiItem.PhotoItem(
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    VIDEO -> MediaPickerUiItem.VideoItem(
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    AUDIO, DOCUMENT -> MediaPickerUiItem.FileItem(
                            uri = it.uri,
                            fileName = it.name ?: "",
                            fileExtension = fileExtension,
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
        selectedUris: List<UriWrapper>?,
        items: List<MediaItem>?
    ): ActionModeUiModel {
        val numSelected = selectedUris?.size ?: 0
        if (selectedUris.isNullOrEmpty()) {
            return ActionModeUiModel.Hidden
        }
        val title: UiString? = when {
            numSelected == 0 -> null
            mediaPickerSetup.canMultiselect -> {
                UiStringText(String.format(resourceProvider.getString(R.string.cab_selected), numSelected))
            }
            else -> {
                val isImagePicker = mediaPickerSetup.allowedTypes.contains(IMAGE)
                val isVideoPicker = mediaPickerSetup.allowedTypes.contains(VIDEO)
                if (isImagePicker && isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_media)
                } else if (isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_video)
                } else {
                    UiStringRes(R.string.photo_picker_use_photo)
                }
            }
        }
        val isVideoSelected = items?.any { it.type == VIDEO && selectedUris.contains(it.uri) } ?: false
        return ActionModeUiModel.Visible(
                title,
                showEditAction = mediaPickerSetup.allowedTypes.contains(IMAGE) && !isVideoSelected
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
        if (!_selectedUris.value.isNullOrEmpty()) {
            _selectedUris.postValue(listOf())
        }
    }

    fun start(
        selectedUris: List<UriWrapper>?,
        mediaPickerSetup: MediaPickerSetup,
        lastTappedIcon: MediaPickerIcon?,
        site: SiteModel?
    ) {
        this.mediaLoader = mediaLoaderFactory.build(mediaPickerSetup.dataSource)
        selectedUris?.let {
            _selectedUris.value = selectedUris
        }
        this.mediaPickerSetup = mediaPickerSetup
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
            loadActions.send(LoadAction.Start(mediaPickerSetup.allowedTypes))
        }
    }

    fun numSelected(): Int {
        return _selectedUris.value?.size ?: 0
    }

    fun selectedURIs(): List<UriWrapper> {
        return _selectedUris.value ?: listOf()
    }

    private fun toggleItem(uri: UriWrapper, canMultiselect: Boolean) {
        val updatedUris = _selectedUris.value?.toMutableList() ?: mutableListOf()
        if (updatedUris.contains(uri)) {
            updatedUris.remove(uri)
        } else {
            if (updatedUris.isNotEmpty() && !canMultiselect) {
                updatedUris.clear()
            }
            updatedUris.add(uri)
        }
        _selectedUris.postValue(updatedUris)
    }

    private fun clickItem(uri: UriWrapper?, isVideo: Boolean) {
        trackOpenPreviewScreenEvent(uri, isVideo)
        uri?.let {
            _navigateToPreview.postValue(Event(it))
        }
    }

    private fun trackOpenPreviewScreenEvent(uri: UriWrapper?, isVideo: Boolean) {
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
        _onIconClicked.postValue(Event(IconClickEvent(icon, mediaPickerSetup.canMultiselect)))
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
                val permissionName = ("<strong>${
                    WPPermissionUtils.getPermissionName(
                            context,
                            permission.WRITE_EXTERNAL_STORAGE
                    )
                }</strong>")
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
