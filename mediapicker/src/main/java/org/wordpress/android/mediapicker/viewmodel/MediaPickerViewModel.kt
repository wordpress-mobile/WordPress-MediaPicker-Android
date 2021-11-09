package org.wordpress.android.mediapicker.viewmodel

import android.Manifest.permission
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.wordpress.android.mediapicker.MediaManager
import org.wordpress.android.mediapicker.MediaPickerTracker
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.R.string
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MimeTypeProvider
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.RemoteId
import org.wordpress.android.mediapicker.model.MediaNavigationEvent
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ChooseMediaPickerAction
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.Exit
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.PreviewMedia
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.PreviewUrl
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.RequestCameraPermission
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.RequestStoragePermission
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ReturnCapturedImage
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ReturnSelectedMedia
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ShowAppSettings
import org.wordpress.android.mediapicker.model.MediaPickerAction
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.model.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.model.MediaPickerContext
import org.wordpress.android.mediapicker.model.MediaPickerContext.MEDIA_FILE
import org.wordpress.android.mediapicker.model.MediaPickerContext.PHOTO_OR_VIDEO
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.ClickAction
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.NextPageLoader
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.ToggleAction
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.VideoItem
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.UiString
import org.wordpress.android.mediapicker.model.UiString.UiStringRes
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.CapturePhoto
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.ChooseFromAndroidDevice
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.SwitchSource
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils
import org.wordpress.android.mediapicker.util.MediaPickerUtils
import org.wordpress.android.mediapicker.util.distinct
import org.wordpress.android.mediapicker.util.merge
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested.STORAGE
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PhotoListUiModel.Data
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PhotoListUiModel.Empty
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PhotoListUiModel.Loading
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.SearchUiModel.Collapsed
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.SearchUiModel.Expanded
import java.security.InvalidParameterException
import javax.inject.Inject

@HiltViewModel
internal class MediaPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaSourceFactory: MediaLoaderFactory,
    private val mediaPickerTracker: MediaPickerTracker,
    private val permissionsHandler: MediaPickerPermissionUtils,
    private val resourceProvider: ResourceProvider,
    private val mimeTypeProvider: MimeTypeProvider,
    private val mediaPickerUtils: MediaPickerUtils,
    private val mediaManager: MediaManager
) : ViewModel() {
    companion object {
        private const val CAPTURED_PHOTO_PATH = "CAPTURED_PHOTO_PATH"
    }
    private lateinit var mediaLoader: MediaLoader
    private val loadActions = Channel<LoadAction>()
    private var searchJob: Job? = null
    private val _domainModel = savedStateHandle.getLiveData<DomainModel>("domain")
    private val _selectedIds = savedStateHandle.getLiveData<List<Identifier>>("selectedIds")
    private val _softAskRequest = savedStateHandle.getLiveData<SoftAskRequest>("softAsk")
    private val _searchExpanded = savedStateHandle.getLiveData<Boolean>("searchExpanded")
    private val _onNavigate = MutableLiveData<Event<MediaNavigationEvent>>()

    val onNavigate = _onNavigate as LiveData<Event<MediaNavigationEvent>>

    val uiState: LiveData<MediaPickerUiState> = merge(
        _domainModel.distinct(),
        _selectedIds.distinct(),
        _softAskRequest,
        _searchExpanded,
    ) { domainModel, selectedIds, softAskRequest, searchExpanded ->
        MediaPickerUiState(
            photoListUiModel = buildUiModel(
                domainModel,
                selectedIds,
                softAskRequest,
                searchExpanded
            ),
            softAskViewUiModel = buildSoftAskView(softAskRequest),
            fabUiModel = FabUiModel(
                show = mediaPickerSetup.availableDataSources.contains(DataSource.CAMERA) &&
                    selectedIds.isNullOrEmpty(),
                action = this::clickOnCamera
            ),
            actionModeUiModel = buildActionModeUiModel(selectedIds),
            searchUiModel = buildSearchUiModel(
                isVisible = softAskRequest?.show != true &&
                    mediaPickerSetup.primaryDataSource != DataSource.CAMERA,
                filter = domainModel?.filter,
                searchExpanded = searchExpanded
            ),
            isRefreshing = !domainModel?.domainItems.isNullOrEmpty() &&
                domainModel?.isLoading == true,
            browseMenuUiModel = buildBrowseMenuUiModel(softAskRequest, searchExpanded)
        )
    }

    private var capturedPhotoPath: String?
        get() = savedStateHandle.get<String>(CAPTURED_PHOTO_PATH)
        set(value) {
            savedStateHandle[CAPTURED_PHOTO_PATH] = value
        }

    val selectedIdentifiers: List<Identifier>
        get() = _selectedIds.value ?: listOf()

    private fun buildSearchUiModel(isVisible: Boolean, filter: String?, searchExpanded: Boolean?): SearchUiModel {
        return when {
            searchExpanded == true -> Expanded(filter ?: "", !mediaPickerSetup.isSearchToggledByDefault)
            isVisible -> Collapsed
            else -> SearchUiModel.Hidden
        }
    }

    private fun buildBrowseMenuUiModel(
        softAskRequest: SoftAskRequest?,
        searchExpanded: Boolean?
    ): BrowseMenuUiModel {
        val isSoftAskRequestVisible = softAskRequest?.show == true
        val isSearchExpanded = searchExpanded ?: false
        val showActions = !isSoftAskRequestVisible && !isSearchExpanded

        val actions = if (showActions) {
            mediaPickerSetup.availableDataSources
        } else {
            emptyList()
        }
        return BrowseMenuUiModel(actions.toSet())
    }

    var lastTappedAction: org.wordpress.android.mediapicker.ui.MediaPickerActionEvent? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    private fun buildUiModel(
        domainModel: DomainModel?,
        selectedIds: List<Identifier>?,
        softAskRequest: SoftAskRequest?,
        isSearching: Boolean?
    ): PhotoListUiModel {
        val data = domainModel?.domainItems
        return if (softAskRequest?.show == true) {
            PhotoListUiModel.Hidden
        } else if (data != null && data.isNotEmpty()) {
            val uiItems = data.map {
                val showOrderCounter = mediaPickerSetup.isMultiSelectEnabled
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
                    org.wordpress.android.util.MediaUtils.getExtensionForMimeType(mimeType)
                        .uppercase()
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
                val loaderItem = if (domainModel.emptyState?.isError == true) {
                    NextPageLoader(false) {
                        viewModelScope.launch {
                            retry()
                        }
                    }
                } else {
                    NextPageLoader(true) {
                        viewModelScope.launch {
                            loadActions.send(NextPage)
                        }
                    }
                }
                updatedItems.add(loaderItem)
                Data(items = updatedItems)
            } else {
                Data(items = uiItems)
            }
        } else if (domainModel?.emptyState != null) {
            with(domainModel.emptyState!!) {
                Empty(
                    title,
                    htmlSubtitle,
                    image ?: drawable.media_picker_lib_empty_search_image,
                    bottomImage,
                    bottomImageDescription,
                    isSearching == true,
                    retryAction = if (isError) {
                        { retry() }
                    } else {
                        null
                    }
                )
            }
        } else if (domainModel?.isLoading == true) {
            Loading
        } else {
            Empty(
                UiStringRes(string.media_empty_list),
                image = drawable.media_picker_lib_empty_gallery_image,
                isSearching = isSearching == true
            )
        }
    }

    private fun buildActionModeUiModel(
        selectedIds: List<Identifier>?
    ): ActionModeUiModel {
        val numSelected = selectedIds?.size ?: 0
        if (selectedIds.isNullOrEmpty()) {
            return ActionModeUiModel.Hidden
        }
        val title: UiString? = when {
            numSelected == 0 -> null
            mediaPickerSetup.isMultiSelectEnabled -> {
                UiString.UiStringText(
                    String.format(
                        resourceProvider.getString(string.cab_selected),
                        numSelected
                    )
                )
            }
            else -> {
                val isImagePicker = mediaPickerSetup.allowedTypes.contains(IMAGE)
                val isVideoPicker = mediaPickerSetup.allowedTypes.contains(VIDEO)
                val isAudioPicker = mediaPickerSetup.allowedTypes.contains(AUDIO)
                if (isImagePicker && isVideoPicker) {
                    UiStringRes(string.photo_picker_use_media)
                } else if (isVideoPicker) {
                    UiStringRes(string.photo_picker_use_video)
                } else if (isAudioPicker) {
                    UiStringRes(string.photo_picker_use_audio)
                } else {
                    UiStringRes(string.photo_picker_use_photo)
                }
            }
        }

        return ActionModeUiModel.Visible(title)
    }

    fun refreshData(forceReload: Boolean) {
        if (!permissionsHandler.hasReadStoragePermission()) {
            return
        }
        viewModelScope.launch {
            loadActions.send(LoadAction.Refresh(forceReload))
        }
    }

    private fun retry() {
        viewModelScope.launch {
            loadActions.send(LoadAction.Retry)
        }
    }

    fun clearSelection() {
        if (!_selectedIds.value.isNullOrEmpty()) {
            mediaPickerTracker.trackSelectionCleared(mediaPickerSetup)
            _selectedIds.postValue(listOf())
        }
    }

    fun start(
        selectedIds: List<Identifier>,
        mediaPickerSetup: MediaPickerSetup,
        lastTappedAction: org.wordpress.android.mediapicker.ui.MediaPickerActionEvent?
    ) {
        _selectedIds.value = selectedIds

        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedAction = lastTappedAction
        this.mediaLoader = mediaSourceFactory.build(mediaPickerSetup)

        if (_domainModel.value == null) {
            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA) {
                startCamera()
            } else if (mediaPickerSetup.primaryDataSource == SYSTEM_PICKER) {
                startSystemPicker()
            }

            mediaPickerTracker.trackMediaPickerOpened(mediaPickerSetup)

            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA ||
                mediaPickerSetup.primaryDataSource == SYSTEM_PICKER
            ) {
                _domainModel.value = DomainModel(isLoading = true)
            } else {
                viewModelScope.launch {
                    mediaLoader.loadMedia(loadActions).collect { domainModel ->
                        _domainModel.value = domainModel
                    }
                }
            }

            if (!mediaPickerSetup.isStoragePermissionRequired || permissionsHandler.hasReadStoragePermission()) {
                viewModelScope.launch {
                    loadActions.send(LoadAction.Start())
                }
            }
        }
        if (mediaPickerSetup.isSearchToggledByDefault) {
            _searchExpanded.postValue(true)
        }
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
        viewModelScope.launch {
            mediaPickerTracker.trackPreview(isVideo, identifier, mediaPickerSetup)
        }
        when (identifier) {
            is LocalUri -> {
                _onNavigate.postValue(Event(PreviewUrl(identifier.uri.toString())))
            }
            is RemoteId -> {
                viewModelScope.launch {
                    _onNavigate.postValue(Event(PreviewMedia(identifier.value)))
                }
            }
        }
    }

    fun performInsertAction() = insertIdentifiers(selectedIdentifiers)

    private fun insertIdentifiers(ids: List<Identifier>) {
        viewModelScope.launch {
            mediaPickerTracker.trackItemsPicked(ids, mediaPickerSetup)
            _onNavigate.value = Event(ReturnSelectedMedia(ids))
        }
    }

    fun onCameraPermissionsGranted() {
        startCamera()
    }

    private fun startCamera() {
        if (!permissionsHandler.hasPermissionsToTakePhotos(
                mediaPickerSetup.isStoragePermissionRequired
            )
        ) {
            lastTappedAction = CapturePhoto
            return
        }
        _onNavigate.postValue(
            Event(
                populateIconClickEvent(
                    CapturePhoto,
                    mediaPickerSetup.isMultiSelectEnabled
                )
            )
        )
    }

    private fun startSystemPicker() {
        clickIcon(ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes))
    }

    private fun clickIcon(action: org.wordpress.android.mediapicker.ui.MediaPickerActionEvent) {
        mediaPickerTracker.trackIconClick(action, mediaPickerSetup)
        if (action is CapturePhoto) {
            if (!permissionsHandler.hasPermissionsToTakePhotos(mediaPickerSetup.isStoragePermissionRequired)) {
                _onNavigate.value = Event(RequestCameraPermission)
                lastTappedAction = action
                return
            }
        }
        _onNavigate.postValue(
            Event(
                populateIconClickEvent(
                    action,
                    mediaPickerSetup.isMultiSelectEnabled
                )
            )
        )
    }

    private fun clickOnCamera() {
        if (mediaPickerSetup.availableDataSources.contains(DataSource.CAMERA)) {
            clickIcon(CapturePhoto)
        }
    }

    private fun populateIconClickEvent(action: org.wordpress.android.mediapicker.ui.MediaPickerActionEvent, canMultiselect: Boolean): ChooseMediaPickerAction {
        val actionEvent: MediaPickerAction = when (action) {
            is ChooseFromAndroidDevice -> {
                getSystemPickerAction(action.allowedTypes, canMultiselect)
            }
            is CapturePhoto -> {
                capturedPhotoPath = mediaPickerUtils.generateCapturedImagePath()
                OpenCameraForPhotos(capturedPhotoPath)
            }
            is SwitchSource -> {
                val availableSources = mutableSetOf<DataSource>().apply {
                    if (action.dataSource == DEVICE) add(SYSTEM_PICKER)
                }
                SwitchMediaPicker(
                    mediaPickerSetup.copy(
                        primaryDataSource = action.dataSource,
                        availableDataSources = availableSources,
                        isSearchToggledByDefault = false
                    )
                )
            }
        }

        return ChooseMediaPickerAction(actionEvent)
    }

    private fun getSystemPickerAction(
        allowedTypes: Set<MediaType>,
        canMultiselect: Boolean
    ): OpenSystemPicker {
        val (context, types) = when {
            listOf(IMAGE).containsAll(allowedTypes) -> {
                Pair(MediaPickerContext.PHOTO, mimeTypeProvider.imageTypes)
            }
            listOf(VIDEO).containsAll(allowedTypes) -> {
                Pair(MediaPickerContext.VIDEO, mimeTypeProvider.videoTypes)
            }
            listOf(IMAGE, VIDEO).containsAll(allowedTypes) -> {
                Pair(
                    PHOTO_OR_VIDEO,
                    mimeTypeProvider.imageTypes + mimeTypeProvider.videoTypes
                )
            }
            listOf(AUDIO).containsAll(allowedTypes) -> {
                Pair(MediaPickerContext.AUDIO, mimeTypeProvider.audioTypes)
            }
            else -> {
                val allTypes = with(mimeTypeProvider) {
                    imageTypes + videoTypes + audioTypes
                }
                Pair(MEDIA_FILE, allTypes)
            }
        }
        return OpenSystemPicker(context, types.toList(), canMultiselect)
    }

    fun checkStoragePermission(isAlwaysDenied: Boolean) {
        if (!mediaPickerSetup.isStoragePermissionRequired) {
            return
        }

        if (permissionsHandler.hasReadStoragePermission()) {
            hideSoftRequest(STORAGE)
        } else {
            showSoftRequest(permission = STORAGE, isAlwaysDenied = isAlwaysDenied)
        }
    }

    fun checkCameraPermission(isCameraPermissionAlwaysDenied: Boolean) {
        if (!permissionsHandler.hasPermissionsToTakePhotos(
                mediaPickerSetup.isStoragePermissionRequired
            )
        ) {
            showSoftRequest(permission = CAMERA, isAlwaysDenied = isCameraPermissionAlwaysDenied)
        } else {
            _domainModel.value = _domainModel.value?.copy(isLoading = true, emptyState = null)
            if (_softAskRequest.value?.show == true) {
                startCamera()
                hideSoftRequest(CAMERA)
            }
        }
    }

    private fun hideSoftRequest(permission: PermissionsRequested) {
        _softAskRequest.value = SoftAskRequest(show = false)

        if (permission == STORAGE && _domainModel.value?.domainItems.isNullOrEmpty()) {
            refreshData(false)
        }
    }

    private fun showSoftRequest(permission: PermissionsRequested, isAlwaysDenied: Boolean) {
        _softAskRequest.value = SoftAskRequest(
            show = true,
            type = permission,
            isAlwaysDenied = isAlwaysDenied
        )
        _domainModel.value = _domainModel.value?.copy(isLoading = false, emptyState = null)
    }

    fun onMenuItemClicked(action: DataSource) {
        val icon = when (action) {
            DEVICE -> SwitchSource(DEVICE)
            SYSTEM_PICKER -> ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes)
            GIF_LIBRARY -> SwitchSource(GIF_LIBRARY)
            else -> throw InvalidParameterException()
        }
        clickIcon(icon)
    }

    private fun buildSoftAskView(softAskRequest: SoftAskRequest?): SoftAskViewUiModel {
        if (softAskRequest != null && softAskRequest.show) {
            mediaPickerTracker.trackShowPermissionsScreen(
                mediaPickerSetup,
                softAskRequest.type,
                softAskRequest.isAlwaysDenied
            )
            val alsoStorageAccess = mediaPickerSetup.isStoragePermissionRequired
            val label = if (softAskRequest.isAlwaysDenied) {
                val storage = permissionsHandler.getPermissionName(permission.READ_EXTERNAL_STORAGE)
                val camera = permissionsHandler.getPermissionName(permission.CAMERA)
                val permission = (
                    "<strong>${
                    when (softAskRequest.type) {
                        STORAGE -> storage
                        CAMERA -> camera + if (alsoStorageAccess) "& $storage" else ""
                    }
                    }</strong>"
                    )
                String.format(
                    resourceProvider.getString(string.media_picker_soft_ask_permissions_denied),
                    permission
                )
            } else {
                when (softAskRequest.type) {
                    STORAGE -> resourceProvider.getString(string.photo_picker_soft_ask_label)
                    CAMERA ->
                        if (alsoStorageAccess) {
                            resourceProvider.getString(string.camera_and_files_soft_ask_label)
                        } else {
                            resourceProvider.getString(string.camera_soft_ask_label)
                        }
                }
            }
            val allowId = if (softAskRequest.isAlwaysDenied) {
                string.button_edit_permissions
            } else {
                string.photo_picker_soft_ask_allow
            }
            return SoftAskViewUiModel.Visible(
                label,
                UiStringRes(allowId),
                softAskRequest.isAlwaysDenied,
                onClick = {
                    onPermissionRequested(softAskRequest.type, softAskRequest.isAlwaysDenied)
                }
            )
        } else {
            return SoftAskViewUiModel.Hidden
        }
    }

    fun onSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            mediaPickerTracker.trackSearch(mediaPickerSetup)
            loadActions.send(LoadAction.Filter(query))
        }
    }

    fun onSearchExpanded() {
        mediaPickerTracker.trackSearchExpanded(mediaPickerSetup)
        _searchExpanded.value = true
    }

    fun onSearchCollapsed() {
        if (!mediaPickerSetup.isSearchToggledByDefault) {
            _searchExpanded.value = false
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                mediaPickerTracker.trackSearchCollapsed(mediaPickerSetup)
                loadActions.send(LoadAction.ClearFilter)
            }
        } else {
            _onNavigate.postValue(Event(Exit))
        }
    }

    fun onPullToRefresh() {
        refreshData(true)
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }

    fun onUrisSelectedFromSystemPicker(uris: List<MediaUri>) {
        viewModelScope.launch {
            delay(100)
            insertIdentifiers(uris.map { LocalUri(it) })
        }
    }

    fun onImageCaptured() {
        capturedPhotoPath?.let {
            viewModelScope.launch {
                mediaManager.addImageToMediaStore(it)?.let { mediaStoreUri ->
                    _onNavigate.value = Event(
                        ReturnCapturedImage(
                            mediaPickerSetup.areResultsQueued,
                            mediaStoreUri
                        )
                    )
                }
            }
        }
    }

    fun onMediaPickerActionFailed() {
        _onNavigate.value = Event(Exit)
    }

    private fun onPermissionRequested(
        permission: PermissionsRequested,
        isAlwaysDenied: Boolean
    ) {
        val navigationEvent = when (permission) {
            CAMERA -> RequestCameraPermission
            STORAGE -> RequestStoragePermission
        }
        if (isAlwaysDenied) {
            _onNavigate.postValue(Event(ShowAppSettings))
        } else {
            _onNavigate.postValue(Event(navigationEvent))
        }
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

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    @Parcelize
    data class SoftAskRequest(
        val show: Boolean,
        val type: PermissionsRequested = STORAGE,
        val isAlwaysDenied: Boolean = false,
    ) : Parcelable

    @Parcelize
    data class SnackbarMessageHolder(
        val message: UiString,
        val buttonTitle: UiString? = null,
        val buttonAction: () -> Unit = {},
        val onDismissAction: () -> Unit = {}
    ) : Parcelable

    sealed class ProgressDialogUiModel : Parcelable {
        @Parcelize object Hidden : ProgressDialogUiModel()
        @Parcelize data class Visible(val title: Int, val cancelAction: () -> Unit) : ProgressDialogUiModel()
    }
}
