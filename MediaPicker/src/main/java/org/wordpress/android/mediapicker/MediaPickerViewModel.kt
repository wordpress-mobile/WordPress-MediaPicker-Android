package org.wordpress.android.mediapicker

import android.Manifest.permission
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
import org.wordpress.android.fluxc.utils.MimeTypes
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.mediapicker.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.MediaPickerFragment.ChooserContext
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenCameraForWPStories
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon.ChooseFromAndroidDevice
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon.WpStoriesCapture
import org.wordpress.android.mediapicker.MediaPickerUiItem.ClickAction
import org.wordpress.android.mediapicker.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.MediaPickerUiItem.ToggleAction
import org.wordpress.android.mediapicker.MediaPickerUiItem.VideoItem
import org.wordpress.android.mediapicker.MediaType.AUDIO
import org.wordpress.android.mediapicker.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.ui.photopicker.PermissionsHandler
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.LocaleManagerWrapper
import org.wordpress.android.util.MediaUtils
import org.wordpress.android.util.MediaUtilsWrapper
import org.wordpress.android.util.UriWrapper
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
    private val localeManagerWrapper: LocaleManagerWrapper,
    private val mediaUtilsWrapper: MediaUtilsWrapper,
    private val resourceProvider: ResourceProvider
) : ScopedViewModel(mainDispatcher) {
    private lateinit var mediaLoader: MediaLoader
    private val loadActions = Channel<LoadAction>()
    private val _navigateToPreview = MutableLiveData<Event<UriWrapper>>()
    private val _navigateToEdit = MutableLiveData<Event<List<UriWrapper>>>()
    private val _onInsert = MutableLiveData<Event<List<UriWrapper>>>()
    private val _domainModel = MutableLiveData<DomainModel>()
    private val _selectedUris = MutableLiveData<List<UriWrapper>>()
    private val _onIconClicked = MutableLiveData<Event<IconClickEvent>>()
    private val _onPermissionsRequested = MutableLiveData<Event<PermissionsRequested>>()
    private val _softAskRequest = MutableLiveData<SoftAskRequest>()
    private val _searchExpanded = MutableLiveData<Boolean>()

    val onNavigateToPreview: LiveData<Event<UriWrapper>> = _navigateToPreview
    val onNavigateToEdit: LiveData<Event<List<UriWrapper>>> = _navigateToEdit
    val onInsert: LiveData<Event<List<UriWrapper>>> = _onInsert
    val onIconClicked: LiveData<Event<IconClickEvent>> = _onIconClicked

    val onPermissionsRequested: LiveData<Event<PermissionsRequested>> = _onPermissionsRequested

    val selectedUris: LiveData<List<UriWrapper>> = _selectedUris

    val uiState: LiveData<MediaPickerUiState> = merge(
            _domainModel.distinct(),
            _selectedUris.distinct(),
            _softAskRequest,
            _searchExpanded
    ) { domainModel, selectedUris, softAskRequest, searchExpanded ->
        MediaPickerUiState(
                buildUiModel(domainModel, selectedUris, softAskRequest),
                buildSoftAskView(softAskRequest),
                FabUiModel(mediaPickerSetup.cameraEnabled && selectedUris.isNullOrEmpty()) {
                    clickIcon(WpStoriesCapture)
                },
                buildActionModeUiModel(selectedUris, domainModel?.domainItems),
                buildSearchUiModel(softAskRequest?.let { !it.show } ?: true, domainModel?.filter, searchExpanded),
                !domainModel?.domainItems.isNullOrEmpty() && domainModel?.isLoading == true,
                buildBrowseMenuUiModel(softAskRequest, searchExpanded)
        )
    }

    private fun buildSearchUiModel(isVisible: Boolean, filter: String?, searchExpanded: Boolean?): SearchUiModel {
        return when {
            searchExpanded == true -> SearchUiModel.Expanded(filter ?: "")
            isVisible -> SearchUiModel.Collapsed
            else -> SearchUiModel.Hidden
        }
    }

    private fun buildBrowseMenuUiModel(softAskRequest: SoftAskRequest?, searchExpanded: Boolean?): BrowseMenuUiModel {
        val isSoftAskRequestVisible = softAskRequest?.show ?: false
        val isSearchExpanded = searchExpanded ?: false
        return BrowseMenuUiModel(!isSoftAskRequestVisible && !isSearchExpanded)
    }

    var lastTappedIcon: MediaPickerIcon? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup
    private var site: SiteModel? = null

    private fun buildUiModel(
        domainModel: DomainModel?,
        selectedUris: List<UriWrapper>?,
        softAskRequest: SoftAskRequest?
    ): PhotoListUiModel {
        val data = domainModel?.domainItems
        return if (null != softAskRequest && softAskRequest.show) {
            PhotoListUiModel.Hidden
        } else if (data != null) {
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
                    mediaUtilsWrapper.getExtensionForMimeType(mimeType).toUpperCase(localeManagerWrapper.getLocale())
                }
                when (it.type) {
                    IMAGE -> PhotoItem(
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    VIDEO -> VideoItem(
                            uri = it.uri,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    AUDIO, DOCUMENT -> FileItem(
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
            if (domainModel.hasMore) {
                val updatedItems = uiItems.toMutableList()
                updatedItems.add(MediaPickerUiItem.NextPageLoader(true, domainModel.error) {
                    launch {
                        loadActions.send(NextPage)
                    }
                })
                PhotoListUiModel.Data(updatedItems)
            } else {
                PhotoListUiModel.Data(uiItems)
            }
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
        val onlyImagesSelected = items?.any { it.type != IMAGE && selectedUris.contains(it.uri) } ?: false
        val showEditActionButton = mediaPickerSetup.allowedTypes.contains(IMAGE) && !onlyImagesSelected
        return ActionModeUiModel.Visible(
                title,
                EditActionUiModel(
                        isVisible = showEditActionButton,
                        isCounterBadgeVisible = if (!showEditActionButton) {
                            false
                        } else {
                            mediaPickerSetup.canMultiselect
                        },
                        counterBadgeValue = numSelected
                )
        )
    }

    fun refreshData(forceReload: Boolean) {
        if (!permissionsHandler.hasStoragePermission()) {
            return
        }
        launch(bgDispatcher) {
            loadActions.send(LoadAction.Refresh(forceReload))
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
        selectedUris?.let {
            _selectedUris.value = selectedUris
        }
        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedIcon = lastTappedIcon
        this.site = site
        if (_domainModel.value == null) {
            this.mediaLoader = mediaLoaderFactory.build(mediaPickerSetup)
            launch(bgDispatcher) {
                mediaLoader.loadMedia(loadActions).collect { domainModel ->
                    withContext(mainDispatcher) {
                        _domainModel.value = domainModel
                    }
                }
            }
            launch(bgDispatcher) {
                loadActions.send(LoadAction.Start())
            }
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
        if (icon is WpStoriesCapture) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onPermissionsRequested.value = Event(PermissionsRequested.CAMERA)
                lastTappedIcon = icon
                return
            }
            AnalyticsTracker.track(MEDIA_PICKER_OPEN_WP_STORIES_CAPTURE)
        }

        // Do we need tracking here?; review tracking need.

        _onIconClicked.postValue(Event(populateIconClickEvent(icon, mediaPickerSetup.canMultiselect)))
    }

    private fun populateIconClickEvent(icon: MediaPickerIcon, canMultiselect: Boolean): IconClickEvent {
        val action: MediaPickerAction = when (icon) {
            is ChooseFromAndroidDevice -> {
                val allowedTypes = icon.allowedTypes
                val (context, types) = when {
                    listOf(IMAGE).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.PHOTO, MimeTypes().getImageTypesOnly())
                    }
                    listOf(VIDEO).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.VIDEO, MimeTypes().getVideoTypesOnly())
                    }
                    listOf(IMAGE, VIDEO).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.PHOTO_OR_VIDEO, MimeTypes().getVideoAndImageTypesOnly())
                    }
                    else -> {
                        Pair(ChooserContext.MEDIA_FILE, MimeTypes().getAllTypes())
                    }
                }
                OpenSystemPicker(context, types.toList(), canMultiselect)
            }
            is WpStoriesCapture -> OpenCameraForWPStories(canMultiselect)
        }

        return IconClickEvent(action)
    }

    fun checkStoragePermission(isAlwaysDenied: Boolean) {
        if (permissionsHandler.hasStoragePermission()) {
            _softAskRequest.value = SoftAskRequest(show = false, isAlwaysDenied = isAlwaysDenied)
            if (_domainModel.value?.domainItems.isNullOrEmpty()) {
                refreshData(false)
            }
        } else {
            _softAskRequest.value = SoftAskRequest(show = true, isAlwaysDenied = isAlwaysDenied)
        }
    }

    fun onBrowseForItems() {
        clickIcon(ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes))
    }

    private fun buildSoftAskView(softAskRequest: SoftAskRequest?): SoftAskViewUiModel {
        if (softAskRequest != null && softAskRequest.show) {
            val appName = "<strong>${resourceProvider.getString(R.string.app_name)}</strong>"
            val label = if (softAskRequest.isAlwaysDenied) {
                val permissionName = ("<strong>${
                    WPPermissionUtils.getPermissionName(
                            resourceProvider,
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

    fun onSearch(query: String) {
        launch(bgDispatcher) {
            loadActions.send(LoadAction.Filter(query))
        }
    }

    fun onSearchExpanded() {
        _searchExpanded.value = true
    }

    fun onSearchCollapsed() {
        _searchExpanded.value = false
        launch(bgDispatcher) {
            loadActions.send(LoadAction.ClearFilter)
        }
    }

    fun onPullToRefresh() {
        refreshData(true)
    }

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
        data class Data(val items: List<MediaPickerUiItem>) :
                PhotoListUiModel()

        object Empty : PhotoListUiModel()
        object Hidden : PhotoListUiModel()
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
            val editActionUiModel: EditActionUiModel = EditActionUiModel()
        ) : ActionModeUiModel()

        object Hidden : ActionModeUiModel()
    }

    sealed class SearchUiModel {
        object Collapsed : SearchUiModel()
        data class Expanded(val filter: String) : SearchUiModel()
        object Hidden : SearchUiModel()
    }

    data class BrowseMenuUiModel(val isVisible: Boolean)

    data class IconClickEvent(val action: MediaPickerAction)

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    data class SoftAskRequest(val show: Boolean, val isAlwaysDenied: Boolean)

    data class EditActionUiModel(
        val isVisible: Boolean = false,
        val isCounterBadgeVisible: Boolean = false,
        val counterBadgeValue: Int = 1
    )
}
