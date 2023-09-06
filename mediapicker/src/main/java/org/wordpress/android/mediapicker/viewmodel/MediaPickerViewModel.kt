package org.wordpress.android.mediapicker.viewmodel

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
import org.wordpress.android.mediapicker.MediaManager
import org.wordpress.android.mediapicker.MediaPickerTracker
import org.wordpress.android.mediapicker.MediaPickerUtils
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.HIDDEN
import org.wordpress.android.mediapicker.api.MediaPickerSetup.SearchMode.VISIBLE_TOGGLED
import org.wordpress.android.mediapicker.api.MimeTypeProvider
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.GifMedia
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.RemoteMedia
import org.wordpress.android.mediapicker.model.MediaNavigationEvent
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ChooseMediaPickerAction
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.Exit
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.PreviewUrl
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.RequestCameraPermission
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.RequestMediaPermissions
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.RequestStoragePermission
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ReturnCapturedImage
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ReturnSelectedMedia
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.ShowAppSettings
import org.wordpress.android.mediapicker.model.MediaPickerAction
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenCameraForPhotos
import org.wordpress.android.mediapicker.model.MediaPickerAction.OpenSystemPicker
import org.wordpress.android.mediapicker.model.MediaPickerAction.SwitchMediaPicker
import org.wordpress.android.mediapicker.model.MediaPickerContext
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.FileItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.LongClickAction
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.NextPageLoader
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.PhotoItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.ToggleAction
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.VideoItem
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.DOCUMENT
import org.wordpress.android.mediapicker.model.MediaType.IMAGE
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.model.MediaTypes
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.UiStateModels.ActionModeUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.BrowseMenuUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.FabUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.MediaPickerUiState
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.READ_STORAGE
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Data
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Empty
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Loading
import org.wordpress.android.mediapicker.model.UiStateModels.SearchUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.SearchUiModel.Collapsed
import org.wordpress.android.mediapicker.model.UiStateModels.SearchUiModel.Expanded
import org.wordpress.android.mediapicker.model.UiStateModels.SoftAskRequest
import org.wordpress.android.mediapicker.model.UiStateModels.SoftAskViewUiModel
import org.wordpress.android.mediapicker.model.UiString
import org.wordpress.android.mediapicker.model.UiString.UiStringRes
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.CapturePhoto
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.ChooseFromAndroidDevice
import org.wordpress.android.mediapicker.ui.MediaPickerActionEvent.SwitchSource
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils
import org.wordpress.android.mediapicker.util.distinct
import org.wordpress.android.mediapicker.util.merge
import javax.inject.Inject
import org.wordpress.android.mediapicker.api.R as MPApiR

@Suppress("LargeClass")
@HiltViewModel
internal class MediaPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaSourceFactory: MediaLoaderFactory,
    private val mediaPickerTracker: MediaPickerTracker,
    private val permissionUtils: MediaPickerPermissionUtils,
    private val resourceProvider: ResourceProvider,
    private val mimeTypeProvider: MimeTypeProvider,
    private val mediaPickerUtils: MediaPickerUtils,
    private val mediaManager: MediaManager,
    private val mediaPickerSetupFactory: MediaPickerSetup.Factory
) : ViewModel() {
    companion object {
        private const val CAPTURED_PHOTO_PATH = "CAPTURED_PHOTO_PATH"
        private const val SEARCH_DELAY = 300L
        private const val SELECTOR_DELAY = 100L
    }

    private lateinit var mediaLoader: MediaLoader
    private val loadActions = Channel<LoadAction>()
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private val _domainModel = savedStateHandle.getLiveData<DomainModel>("domain")
    private val _selectedIds = savedStateHandle.getLiveData<List<Identifier>>("selectedIds")
    private val _softAskRequest = savedStateHandle.getLiveData<SoftAskRequest>("softAsk")
    private val _searchExpanded = savedStateHandle.getLiveData<Boolean>("searchExpanded")
    private val _onNavigate = MutableLiveData<Event<MediaNavigationEvent>>()

    val onNavigate = _onNavigate as LiveData<Event<MediaNavigationEvent>>

    val title by lazy {
        mediaPickerSetup.title
    }

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
                action = this::onCameraClicked
            ),
            actionModeUiModel = buildActionModeUiModel(selectedIds),
            searchUiModel = buildSearchUiModel(
                isVisible = softAskRequest?.show != true && mediaPickerSetup.searchMode != HIDDEN,
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

    private fun buildSearchUiModel(
        isVisible: Boolean,
        filter: String?,
        searchExpanded: Boolean?
    ): SearchUiModel {
        return when {
            searchExpanded == true -> Expanded(
                filter ?: "",
                mediaPickerSetup.searchMode != VISIBLE_TOGGLED
            )

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

    var lastTappedAction: MediaPickerActionEvent? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    @Suppress("LongMethod", "ComplexMethod")
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
                val toggleAction = ToggleAction(it.identifier, showOrderCounter, ::onItemToggled)
                val longClickAction = LongClickAction(
                    it.identifier,
                    it.type == VIDEO,
                    ::onItemLongClicked
                )
                val (selectedOrder, isSelected) = if (selectedIds != null &&
                    selectedIds.contains(it.identifier)
                ) {
                    val selectedOrder = if (showOrderCounter) {
                        selectedIds.indexOf(it.identifier) + 1
                    } else {
                        null
                    }
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
                        longClickAction = longClickAction
                    )

                    VIDEO -> VideoItem(
                        url = it.url,
                        identifier = it.identifier,
                        isSelected = isSelected,
                        selectedOrder = selectedOrder,
                        showOrderCounter = showOrderCounter,
                        toggleAction = toggleAction,
                        longClickAction = longClickAction
                    )

                    AUDIO, DOCUMENT -> FileItem(
                        fileName = it.name ?: "",
                        fileExtension = fileExtension,
                        mimeType = it.mimeType,
                        identifier = it.identifier,
                        isSelected = isSelected,
                        selectedOrder = selectedOrder,
                        showOrderCounter = showOrderCounter,
                        toggleAction = toggleAction,
                        longClickAction = longClickAction
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
                    image ?: MPApiR.drawable.media_picker_lib_empty_search_image,
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
                UiStringRes(R.string.media_empty_list),
                image = MPApiR.drawable.media_picker_lib_empty_gallery_image,
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
                        resourceProvider.getString(R.string.add_count),
                        numSelected
                    )
                )
            }

            else -> {
                val isImagePicker = mediaPickerSetup.allowedTypes.contains(IMAGE)
                val isVideoPicker = mediaPickerSetup.allowedTypes.contains(VIDEO)
                val isAudioPicker = mediaPickerSetup.allowedTypes.contains(AUDIO)
                if (isImagePicker && isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_media)
                } else if (isVideoPicker) {
                    UiStringRes(R.string.photo_picker_use_video)
                } else if (isAudioPicker) {
                    UiStringRes(R.string.photo_picker_use_audio)
                } else {
                    UiStringRes(R.string.photo_picker_use_photo)
                }
            }
        }

        return ActionModeUiModel.Visible(title)
    }

    @Suppress("ComplexCondition")
    private fun refreshData(forceReload: Boolean) {
        if (hasAllRequiredPermissions()) {
            viewModelScope.launch {
                loadActions.send(LoadAction.Refresh(forceReload))
            }
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

    fun restart(mediaPickerSetup: MediaPickerSetup) {
        loadJob?.cancel()
        start(emptyList(), mediaPickerSetup, null, true)
    }

    fun start(
        selectedIds: List<Identifier>,
        mediaPickerSetup: MediaPickerSetup,
        lastTappedAction: MediaPickerActionEvent?,
        reload: Boolean = false
    ) {
        _selectedIds.value = selectedIds

        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedAction = lastTappedAction
        this.mediaLoader = mediaSourceFactory.build(mediaPickerSetup)

        if (_domainModel.value == null || reload) {
            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA) {
                startCamera()
            } else if (mediaPickerSetup.primaryDataSource == SYSTEM_PICKER) {
                startSystemPicker()
            }

            mediaPickerTracker.trackMediaPickerOpened(mediaPickerSetup)

            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA ||
                mediaPickerSetup.primaryDataSource == SYSTEM_PICKER
            ) {
                _domainModel.value = DomainModel(domainItems = emptyList(), isLoading = true)
            } else {
                loadJob = viewModelScope.launch {
                    mediaLoader.loadMedia(loadActions).collect { domainModel ->
                        _domainModel.value = domainModel
                    }
                }
            }

            if (hasAllRequiredPermissions()) {
                viewModelScope.launch {
                    loadActions.send(LoadAction.Start())
                }
            }
        }
        if (mediaPickerSetup.searchMode == VISIBLE_TOGGLED) {
            _searchExpanded.postValue(true)
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        return if (mediaPickerSetup.areMediaPermissionsRequired) {
            val images =
                !mediaPickerSetup.isImagesPermissionRequired || permissionUtils.hasImagesPermission()
            val videos =
                !mediaPickerSetup.isVideoPermissionRequired || permissionUtils.hasVideoPermission()
            val music =
                !mediaPickerSetup.isAudioPermissionRequired || permissionUtils.hasAudioPermission()
            images && videos && music
        } else if (mediaPickerSetup.isReadStoragePermissionRequired) {
            permissionUtils.hasReadStoragePermission()
        } else {
            true
        }
    }

    private fun onItemToggled(identifier: Identifier, canMultiselect: Boolean) {
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

    private fun onItemLongClicked(identifier: Identifier, isVideo: Boolean) {
        viewModelScope.launch {
            mediaPickerTracker.trackPreview(isVideo, identifier, mediaPickerSetup)
        }
        when (identifier) {
            is LocalUri -> {
                _onNavigate.postValue(Event(PreviewUrl(identifier.uri.toString())))
            }

            is RemoteMedia -> {
                _onNavigate.postValue(Event(PreviewUrl(identifier.url)))
            }

            is GifMedia -> {
                _onNavigate.postValue(Event(PreviewUrl(identifier.uri.uri)))
            }

            else -> {
                // not relevant
            }
        }
    }

    fun onSelectionConfirmed() {
        insertIdentifiers(selectedIdentifiers)
    }

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
        if (!permissionUtils.hasPermissionsToTakePhotos()) {
            lastTappedAction = CapturePhoto
            return
        }
        lastTappedAction = null
        triggerAction(CapturePhoto)
    }

    private fun startSystemPicker() {
        triggerAction(ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes))
    }

    private fun triggerAction(action: MediaPickerActionEvent) {
        mediaPickerTracker.trackIconClick(action, mediaPickerSetup)
        _onNavigate.postValue(
            Event(populateActionEvent(action, mediaPickerSetup.isMultiSelectEnabled))
        )
    }

    private fun onCameraClicked() {
        if (mediaPickerSetup.availableDataSources.contains(DataSource.CAMERA)) {
            _onNavigate.postValue(Event(populateActionEvent(SwitchSource(DataSource.CAMERA))))
        }
    }

    private fun populateActionEvent(
        action: MediaPickerActionEvent,
        canMultiselect: Boolean = false
    ): ChooseMediaPickerAction {
        val actionEvent: MediaPickerAction = when (action) {
            is ChooseFromAndroidDevice -> {
                getSystemPickerAction(action.allowedTypes, canMultiselect)
            }

            is CapturePhoto -> {
                capturedPhotoPath = mediaPickerUtils.generateCapturedImagePath()
                OpenCameraForPhotos(capturedPhotoPath)
            }

            is SwitchSource -> {
                SwitchMediaPicker(
                    mediaPickerSetupFactory.build(
                        action.dataSource,
                        MediaTypes.fromAllowedTypes(mediaPickerSetup.allowedTypes),
                        canMultiselect
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
            MediaTypes.IMAGES.allowedTypes == allowedTypes -> {
                Pair(MediaPickerContext.PHOTO, mimeTypeProvider.imageTypes)
            }

            MediaTypes.VIDEOS.allowedTypes == allowedTypes -> {
                Pair(MediaPickerContext.VIDEO, mimeTypeProvider.videoTypes)
            }

            MediaTypes.AUDIOS.allowedTypes == allowedTypes -> {
                Pair(MediaPickerContext.AUDIO, mimeTypeProvider.audioTypes)
            }

            MediaTypes.DOCUMENTS.allowedTypes == allowedTypes -> {
                Pair(MediaPickerContext.FILE, mimeTypeProvider.documentTypes)
            }

            MediaTypes.IMAGES_AND_VIDEOS.allowedTypes == allowedTypes -> {
                Pair(
                    MediaPickerContext.PHOTO_OR_VIDEO,
                    mimeTypeProvider.imageTypes + mimeTypeProvider.videoTypes
                )
            }


            MediaTypes.MEDIA.allowedTypes == allowedTypes -> {
                val allTypes = with(mimeTypeProvider) {
                    imageTypes + videoTypes + audioTypes
                }
                Pair(MediaPickerContext.FILE, allTypes)
            }

            else -> {
                val allTypes = with(mimeTypeProvider) {
                    imageTypes + videoTypes + audioTypes + documentTypes
                }
                Pair(MediaPickerContext.FILE, allTypes)
            }
        }
        return OpenSystemPicker(context, types.toList(), canMultiselect)
    }

    fun checkStoragePermission(isAlwaysDenied: Boolean) {
        if (hasAllRequiredPermissions()) {
            hideSoftRequest(shouldRefreshDataIfEmpty = true)
            return
        } else {
            showSoftRequest(
                permissions = listOf(READ_STORAGE),
                isAlwaysDenied = isAlwaysDenied
            )
        }
    }

    fun checkCameraPermission(areAlwaysDenied: Boolean) {
        if (!permissionUtils.hasPermissionsToTakePhotos()) {
            showSoftRequest(
                permissions = permissionUtils.permissionsForTakingPhotos,
                isAlwaysDenied = areAlwaysDenied
            )
        } else {
            _domainModel.value = _domainModel.value?.copy(
                domainItems = emptyList(),
                isLoading = true,
                emptyState = null
            )
            hideSoftRequest(shouldRefreshDataIfEmpty = false)

            if (lastTappedAction == CapturePhoto) {
                startCamera()
            }
        }
    }

    fun checkMediaPermissions(permissions: List<PermissionsRequested>, isAlwaysDenied: Boolean) {
        if (hasAllRequiredPermissions()) {
            hideSoftRequest(shouldRefreshDataIfEmpty = true)
            return
        } else {
            showSoftRequest(
                permissions = permissions.toList(),
                isAlwaysDenied = isAlwaysDenied
            )
        }
    }

    private fun hideSoftRequest(shouldRefreshDataIfEmpty: Boolean) {
        _softAskRequest.value = SoftAskRequest(show = false)

        if (shouldRefreshDataIfEmpty && _domainModel.value?.domainItems.isNullOrEmpty()) {
            refreshData(false)
        }
    }

    private fun showSoftRequest(permissions: List<PermissionsRequested>, isAlwaysDenied: Boolean) {
        _softAskRequest.value = SoftAskRequest(
            show = true,
            permissions = permissions,
            isAlwaysDenied = isAlwaysDenied
        )
        _domainModel.value = _domainModel.value?.copy(isLoading = false, emptyState = null)
    }

    fun onMenuItemClicked(source: DataSource) {
        triggerAction(SwitchSource(source))
    }

    private fun buildSoftAskView(softAskRequest: SoftAskRequest?): SoftAskViewUiModel {
        if (softAskRequest != null && softAskRequest.show) {
            mediaPickerTracker.trackShowPermissionsScreen(
                mediaPickerSetup,
                softAskRequest.permissions,
                softAskRequest.isAlwaysDenied
            )
            val permissionNames =
                softAskRequest.permissions.map { permission ->
                    "<strong>${
                        permissionUtils.getPermissionName(permission)
                    }</strong>"
                }.distinct().joinToString(" & ")

            val labelStringResource = if (softAskRequest.isAlwaysDenied)
                R.string.media_picker_soft_ask_permissions_denied
            else
                R.string.media_picker_soft_ask_permissions_request
            val label = resourceProvider.getString(labelStringResource, permissionNames)
            val buttonStringResource = if (softAskRequest.isAlwaysDenied) {
                R.string.button_edit_permissions
            } else {
                R.string.photo_picker_soft_ask_allow
            }

            return SoftAskViewUiModel.Visible(
                label,
                UiStringRes(buttonStringResource),
                softAskRequest.isAlwaysDenied,
                onClick = {
                    onPermissionsRequested(
                        permissions = softAskRequest.permissions,
                        isAlwaysDenied = softAskRequest.isAlwaysDenied
                    )
                }
            )
        } else {
            return SoftAskViewUiModel.Hidden
        }
    }

    fun onSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY)
            mediaPickerTracker.trackSearch(mediaPickerSetup)
            loadActions.send(LoadAction.Filter(query))
        }
    }

    fun onSearchExpanded() {
        mediaPickerTracker.trackSearchExpanded(mediaPickerSetup)
        _searchExpanded.value = true
    }

    fun onSearchCollapsed() {
        if (mediaPickerSetup.searchMode != VISIBLE_TOGGLED) {
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
            delay(SELECTOR_DELAY)
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

    private fun onPermissionsRequested(
        permissions: List<PermissionsRequested>,
        isAlwaysDenied: Boolean
    ) {
        val navigationEvent = when {
            permissions.contains(CAMERA) -> RequestCameraPermission(permissions)
            permissions.contains(READ_STORAGE) -> RequestStoragePermission
            else -> RequestMediaPermissions(permissions)
        }
        if (isAlwaysDenied) {
            _onNavigate.postValue(Event(ShowAppSettings))
        } else {
            _onNavigate.postValue(Event(navigationEvent))
        }
    }
}
