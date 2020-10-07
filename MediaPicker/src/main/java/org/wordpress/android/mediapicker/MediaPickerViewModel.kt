package org.wordpress.android.mediapicker

import android.Manifest.permission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.R
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.MediaStore
import org.wordpress.android.fluxc.utils.MimeTypes
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.MediaItem.Identifier.RemoteId
import org.wordpress.android.mediapicker.MediaItem.Identifier.StockMediaIdentifier
import org.wordpress.android.mediapicker.MediaNavigationEvent.EditMedia
import org.wordpress.android.mediapicker.MediaNavigationEvent.Exit
import org.wordpress.android.mediapicker.MediaNavigationEvent.IconClickEvent
import org.wordpress.android.mediapicker.MediaNavigationEvent.PreviewMedia
import org.wordpress.android.mediapicker.MediaNavigationEvent.PreviewUrl
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
import org.wordpress.android.mediapicker.MediaPickerViewModel.ProgressDialogUiModel.Hidden
import org.wordpress.android.mediapicker.MediaPickerViewModel.ProgressDialogUiModel.Visible
import org.wordpress.android.mediapicker.MediaType.AUDIO
import org.wordpress.android.mediapicker.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.MediaType.IMAGE
import org.wordpress.android.mediapicker.MediaType.VIDEO
import org.wordpress.android.mediapicker.insert.MediaInsertHandler
import org.wordpress.android.mediapicker.insert.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.insert.MediaInsertHandlerFactory
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.photopicker.PermissionsHandler
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.ui.utils.UiString.UiStringResWithParams
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.LocaleManagerWrapper
import org.wordpress.android.util.MediaUtilsWrapper
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
    private val mediaInsertHandlerFactory: MediaInsertHandlerFactory,
    private val analyticsUtilsWrapper: AnalyticsUtilsWrapper,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val mediaPickerTracker: MediaPickerTracker,
    private val permissionsHandler: PermissionsHandler,
    private val localeManagerWrapper: LocaleManagerWrapper,
    private val mediaUtilsWrapper: MediaUtilsWrapper,
    private val mediaStore: MediaStore,
    private val resourceProvider: ResourceProvider
) : ScopedViewModel(mainDispatcher) {
    private lateinit var mediaLoader: MediaLoader
    private lateinit var mediaInsertHandler: MediaInsertHandler
    private val loadActions = Channel<LoadAction>()
    private val _domainModel = MutableLiveData<DomainModel>()
    private val _selectedIds = MutableLiveData<List<Identifier>>()
    private val _onPermissionsRequested = MutableLiveData<Event<PermissionsRequested>>()
    private val _softAskRequest = MutableLiveData<SoftAskRequest>()
    private val _searchExpanded = MutableLiveData<Boolean>()
    private val _showProgressDialog = MutableLiveData<ProgressDialogUiModel>()
    private val _onSnackbarMessage = MutableLiveData<Event<SnackbarMessageHolder>>()
    private val _onNavigate = MutableLiveData<Event<MediaNavigationEvent>>()

    val onSnackbarMessage: LiveData<Event<SnackbarMessageHolder>> = _onSnackbarMessage
    val onNavigate = _onNavigate as LiveData<Event<MediaNavigationEvent>>

    val onPermissionsRequested: LiveData<Event<PermissionsRequested>> = _onPermissionsRequested

    val uiState: LiveData<MediaPickerUiState> = merge(
            _domainModel.distinct(),
            _selectedIds.distinct(),
            _softAskRequest,
            _searchExpanded,
            _showProgressDialog.distinct()
    ) { domainModel, selectedIds, softAskRequest, searchExpanded, progressDialogUiModel ->
        MediaPickerUiState(
                buildUiModel(domainModel, selectedIds, softAskRequest),
                buildSoftAskView(softAskRequest),
                FabUiModel(mediaPickerSetup.cameraEnabled && selectedIds.isNullOrEmpty()) {
                    clickIcon(WpStoriesCapture)
                },
                buildActionModeUiModel(selectedIds, domainModel?.domainItems),
                buildSearchUiModel(softAskRequest?.let { !it.show } ?: true, domainModel?.filter, searchExpanded),
                !domainModel?.domainItems.isNullOrEmpty() && domainModel?.isLoading == true,
                buildBrowseMenuUiModel(softAskRequest, searchExpanded),
                progressDialogUiModel ?: Hidden
        )
    }

    private fun buildSearchUiModel(isVisible: Boolean, filter: String?, searchExpanded: Boolean?): SearchUiModel {
        return when {
            searchExpanded == true -> SearchUiModel.Expanded(filter ?: "", !mediaPickerSetup.defaultSearchView)
            isVisible -> SearchUiModel.Collapsed
            else -> SearchUiModel.Hidden
        }
    }

    private fun buildBrowseMenuUiModel(softAskRequest: SoftAskRequest?, searchExpanded: Boolean?): BrowseMenuUiModel {
        val isSoftAskRequestVisible = softAskRequest?.show ?: false
        val isSearchExpanded = searchExpanded ?: false
        return BrowseMenuUiModel(mediaPickerSetup.systemPickerEnabled && !isSoftAskRequestVisible && !isSearchExpanded)
    }

    var lastTappedIcon: MediaPickerIcon? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup
    private var site: SiteModel? = null

    private fun buildUiModel(
        domainModel: DomainModel?,
        selectedIds: List<Identifier>?,
        softAskRequest: SoftAskRequest?
    ): PhotoListUiModel {
        val data = domainModel?.domainItems
        return if (null != softAskRequest && softAskRequest.show) {
            PhotoListUiModel.Hidden
        } else if (data != null && data.isNotEmpty()) {
            val uiItems = data.map {
                val showOrderCounter = mediaPickerSetup.canMultiselect
                val toggleAction = ToggleAction(it.identifier, showOrderCounter, this::toggleItem)
                val clickAction = ClickAction(it.identifier, it.type == VIDEO, this::clickItem)
                val (selectedOrder, isSelected) = if (selectedIds != null && selectedIds.contains(it.identifier)) {
                    val selectedOrder = if (showOrderCounter) selectedIds.indexOf(it.identifier) + 1 else null
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
                            url = it.url,
                            identifier = it.identifier,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    VIDEO -> VideoItem(
                            url = it.url,
                            identifier = it.identifier,
                            isSelected = isSelected,
                            selectedOrder = selectedOrder,
                            showOrderCounter = showOrderCounter,
                            toggleAction = toggleAction,
                            clickAction = clickAction
                    )
                    AUDIO, DOCUMENT -> FileItem(
                            fileName = it.name ?: "",
                            fileExtension = fileExtension,
                            identifier = it.identifier,
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
        } else if (domainModel?.emptyState != null) {
            PhotoListUiModel.Empty(
                    domainModel.emptyState.title,
                    domainModel.emptyState.htmlSubtitle,
                    domainModel.emptyState.image
            )
        } else {
            PhotoListUiModel.Empty(
                    UiStringRes(R.string.media_empty_list),
                    image = R.drawable.img_illustration_media_105dp
            )
        }
    }

    private fun buildActionModeUiModel(
        selectedIds: List<Identifier>?,
        items: List<MediaItem>?
    ): ActionModeUiModel {
        val numSelected = selectedIds?.size ?: 0
        if (selectedIds.isNullOrEmpty()) {
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

        val onlyImagesSelected = items?.none { it.type != IMAGE && selectedIds.contains(it.identifier) } ?: true
        val showEditActionButton = mediaPickerSetup.editingEnabled && onlyImagesSelected
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
        if (!_selectedIds.value.isNullOrEmpty()) {
            _selectedIds.postValue(listOf())
        }
    }

    fun start(
        selectedIds: List<Identifier>?,
        mediaPickerSetup: MediaPickerSetup,
        lastTappedIcon: MediaPickerIcon?,
        site: SiteModel?
    ) {
        _selectedIds.value = selectedIds
        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedIcon = lastTappedIcon
        this.site = site
        if (_domainModel.value == null) {
            mediaPickerTracker.trackMediaPickerOpened(mediaPickerSetup)
            this.mediaLoader = mediaLoaderFactory.build(mediaPickerSetup, site)
            this.mediaInsertHandler = mediaInsertHandlerFactory.build(mediaPickerSetup, site)
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
        if (mediaPickerSetup.defaultSearchView) {
            _searchExpanded.postValue(true)
        }
    }

    fun numSelected(): Int {
        return _selectedIds.value?.size ?: 0
    }

    fun selectedIdentifiers(): List<Identifier> {
        return _selectedIds.value ?: listOf()
    }

    private fun toggleItem(identifier: Identifier, canMultiselect: Boolean) {
        val updatedUris = _selectedIds.value?.toMutableList() ?: mutableListOf()
        if (updatedUris.contains(identifier)) {
            mediaPickerTracker.trackItemUnselected(mediaPickerSetup)
            updatedUris.remove(identifier)
        } else {
            mediaPickerTracker.trackItemSelected(mediaPickerSetup)
            if (updatedUris.isNotEmpty() && !canMultiselect) {
                updatedUris.clear()
            }
            updatedUris.add(identifier)
        }
        _selectedIds.postValue(updatedUris)
    }

    private fun clickItem(identifier: Identifier, isVideo: Boolean) {
        launch {
            mediaPickerTracker.trackPreview(isVideo, identifier, mediaPickerSetup)
        }
        when (identifier) {
            is LocalUri -> {
                _onNavigate.postValue(Event(PreviewUrl(identifier.value.toString())))
            }
            is StockMediaIdentifier -> {
                if (identifier.url != null) {
                    _onNavigate.postValue(Event(PreviewUrl(identifier.url)))
                }
            }
            is RemoteId -> {
                site?.let {
                    launch {
                        val media: MediaModel = mediaStore.getSiteMediaWithId(it, identifier.value)
                        _onNavigate.postValue(Event(PreviewMedia(media)))
                    }
                }
            }
        }
    }

    fun performInsertAction() {
        val ids = selectedIdentifiers()
        var job: Job? = null
        job = launch {
            var progressDialogJob: Job? = null
            mediaInsertHandler.insertMedia(ids).collect {
                when (it) {
                    is InsertModel.Progress -> {
                        progressDialogJob = launch {
                            delay(100)
                            _showProgressDialog.value = Visible(R.string.media_uploading_stock_library_photo) {
                                job?.cancel()
                                _showProgressDialog.value = Hidden
                            }
                        }
                    }
                    is InsertModel.Error -> {
                        val message = if (it.error.isNotEmpty()) {
                            UiStringResWithParams(
                                    R.string.media_insert_failed_with_reason,
                                    listOf(UiStringText(it.error))
                            )
                        } else {
                            UiStringRes(R.string.media_insert_failed)
                        }
                        _onSnackbarMessage.value = Event(
                                SnackbarMessageHolder(
                                        message
                                )
                        )
                        progressDialogJob?.cancel()
                        job = null
                        _showProgressDialog.value = Hidden
                    }
                    is InsertModel.Success -> {
                        launch {
                            mediaPickerTracker.trackItemsPicked(it.identifiers, mediaPickerSetup)
                        }
                        progressDialogJob?.cancel()
                        job = null
                        _showProgressDialog.value = Hidden
                        _onNavigate.value = Event(MediaNavigationEvent.InsertMedia(it.identifiers))
                    }
                }
            }
        }
    }

    fun performEditAction() {
        val uriList = selectedIdentifiers().mapNotNull { (it as? Identifier.LocalUri)?.value }
        _onNavigate.value = Event(EditMedia(uriList))
    }

    fun clickOnLastTappedIcon() = clickIcon(lastTappedIcon!!)

    private fun clickIcon(icon: MediaPickerIcon) {
        mediaPickerTracker.trackIconClick(icon, mediaPickerSetup)
        if (icon is WpStoriesCapture) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onPermissionsRequested.value = Event(PermissionsRequested.CAMERA)
                lastTappedIcon = icon
                return
            }
        }
        // Do we need tracking here?; review tracking need.

        _onNavigate.postValue(Event(populateIconClickEvent(icon, mediaPickerSetup.canMultiselect)))
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
        if (!mediaPickerSetup.requiresStoragePermissions) {
            return
        }
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
            mediaPickerTracker.trackShowPermissionsScreen(mediaPickerSetup, softAskRequest.isAlwaysDenied)
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
            mediaPickerTracker.trackSearch(mediaPickerSetup)
            loadActions.send(LoadAction.Filter(query))
        }
    }

    fun onSearchExpanded() {
        mediaPickerTracker.trackSearchExpanded(mediaPickerSetup)
        _searchExpanded.value = true
    }

    fun onSearchCollapsed() {
        if (!mediaPickerSetup.defaultSearchView) {
            mediaPickerTracker.trackSearchCollapsed(mediaPickerSetup)
            _searchExpanded.value = false
            launch(bgDispatcher) {
                loadActions.send(LoadAction.ClearFilter)
            }
        } else {
            _onNavigate.postValue(Event(Exit))
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
        val browseMenuUiModel: BrowseMenuUiModel,
        val progressDialogUiModel: ProgressDialogUiModel
    )

    sealed class PhotoListUiModel {
        data class Data(val items: List<MediaPickerUiItem>) :
                PhotoListUiModel()

        data class Empty(val title: UiString, val htmlSubtitle: UiString? = null, val image: Int? = null) :
                PhotoListUiModel()

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
        data class Expanded(val filter: String, val closeable: Boolean = true) : SearchUiModel()
        object Hidden : SearchUiModel()
    }

    data class BrowseMenuUiModel(val isVisible: Boolean)

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    data class SoftAskRequest(val show: Boolean, val isAlwaysDenied: Boolean)

    data class EditActionUiModel(
        val isVisible: Boolean = false,
        val isCounterBadgeVisible: Boolean = false,
        val counterBadgeValue: Int = 1
    )

    sealed class ProgressDialogUiModel {
        object Hidden : ProgressDialogUiModel()
        data class Visible(val title: Int, val cancelAction: () -> Unit) : ProgressDialogUiModel()
    }
}
