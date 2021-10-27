package org.wordpress.android.mediapicker.viewmodel

import android.Manifest.permission
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.wordpress.android.mediapicker.model.MediaNavigationEvent
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.*
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.*
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerAction.*
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerIcon.*
import org.wordpress.android.mediapicker.MediaPickerTracker
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.model.MediaPickerUiItem.*
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.R.string
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.BrowseMenuUiModel.BrowseAction
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.ProgressDialogUiModel.Hidden
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.ProgressDialogUiModel.Visible
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MimeTypeProvider
import org.wordpress.android.mediapicker.api.MediaInsertHandler
import org.wordpress.android.mediapicker.api.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.api.MediaInsertHandlerFactory
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.LocalUri
import org.wordpress.android.mediapicker.model.MediaItem.Identifier.RemoteId
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PhotoListUiModel.*
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.SearchUiModel.Collapsed
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.SearchUiModel.Expanded
import org.wordpress.android.mediapicker.model.MediaType.*
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.UiString.UiStringRes
import org.wordpress.android.mediapicker.PermissionsHandler
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.*
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaType.AUDIO
import org.wordpress.android.mediapicker.model.MediaType.VIDEO
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.ChooserContext.*
import org.wordpress.android.mediapicker.util.UiString
import org.wordpress.android.mediapicker.util.distinct
import org.wordpress.android.mediapicker.util.merge
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested.STORAGE
import javax.inject.Inject

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    private val mediaSourceFactory: MediaLoaderFactory,
    private val mediaInsertHandlerFactory: MediaInsertHandlerFactory,
    private val mediaPickerTracker: MediaPickerTracker,
    private val permissionsHandler: PermissionsHandler,
    private val resourceProvider: ResourceProvider,
    private val mimeTypeProvider: MimeTypeProvider
) : ViewModel() {
    private lateinit var mediaLoader: MediaLoader
    private lateinit var mediaInsertHandler: MediaInsertHandler
    private val loadActions = Channel<LoadAction>()
    private var searchJob: Job? = null
    private val _domainModel = MutableLiveData<DomainModel>()
    private val _selectedIds = MutableLiveData<List<Identifier>>()
    private val _softAskRequest = MutableLiveData<SoftAskRequest>()
    private val _searchExpanded = MutableLiveData<Boolean>()
    private val _showProgressDialog = MutableLiveData<ProgressDialogUiModel>()
    private val _onSnackbarMessage = MutableLiveData<Event<SnackbarMessageHolder>>()
    private val _onNavigate = MutableLiveData<Event<MediaNavigationEvent>>()

    val onSnackbarMessage: LiveData<Event<SnackbarMessageHolder>> = _onSnackbarMessage
    val onNavigate = _onNavigate as LiveData<Event<MediaNavigationEvent>>

    val uiState: LiveData<MediaPickerUiState> = merge(
        _domainModel.distinct(),
        _selectedIds.distinct(),
        _softAskRequest,
        _searchExpanded,
        _showProgressDialog.distinct()
    ) { domainModel, selectedIds, softAskRequest, searchExpanded, progressDialogUiModel ->
        MediaPickerUiState(
            photoListUiModel = buildUiModel(
                domainModel,
                selectedIds,
                softAskRequest,
                searchExpanded
            ),
            softAskViewUiModel = buildSoftAskView(softAskRequest),
            fabUiModel = FabUiModel(
                show = mediaPickerSetup.availableDataSources.contains(DataSource.CAMERA)
                        && selectedIds.isNullOrEmpty(),
                action = this::clickOnCamera
            ),
            actionModeUiModel = buildActionModeUiModel(selectedIds),
            searchUiModel = buildSearchUiModel(
                isVisible = softAskRequest?.show != true
                        && mediaPickerSetup.primaryDataSource != DataSource.CAMERA,
                filter = domainModel?.filter,
                searchExpanded = searchExpanded
            ),
            isRefreshing = !domainModel?.domainItems.isNullOrEmpty()
                    && domainModel?.isLoading == true,
            browseMenuUiModel = buildBrowseMenuUiModel(softAskRequest, searchExpanded),
            progressDialogUiModel = progressDialogUiModel ?: Hidden
        )
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
            mediaPickerSetup.availableDataSources.mapNotNull {
                when (it) {
                    DEVICE -> BrowseAction.DEVICE
                    GIF_LIBRARY -> BrowseAction.GIF_LIBRARY
                    SYSTEM_PICKER -> BrowseAction.SYSTEM_PICKER
                    else -> null
                }
            }
        } else {
            emptyList()
        }
        return BrowseMenuUiModel(actions.toSet())
    }

    var lastTappedIcon: MediaPickerIcon? = null
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
                    image ?: drawable.img_illustration_empty_results_216dp,
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
                image = drawable.img_illustration_media_105dp,
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
        if (!permissionsHandler.hasStoragePermission()) {
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
        lastTappedIcon: MediaPickerIcon?
    ) {
        _selectedIds.value = selectedIds
        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedIcon = lastTappedIcon

        if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA) {
            startCamera()
        } else if (mediaPickerSetup.primaryDataSource == SYSTEM_PICKER) {
            startSystemPicker()
        }

        if (_domainModel.value == null) {
            mediaPickerTracker.trackMediaPickerOpened(mediaPickerSetup)

            this.mediaLoader = mediaSourceFactory.build(mediaPickerSetup)
            this.mediaInsertHandler = mediaInsertHandlerFactory.build(mediaPickerSetup)


            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA
                || mediaPickerSetup.primaryDataSource == SYSTEM_PICKER) {
                _domainModel.value = DomainModel(isLoading = true)
            } else {
                viewModelScope.launch {
                    mediaLoader.loadMedia(loadActions).collect { domainModel ->
                        _domainModel.value = domainModel
                    }
                }
            }

            if (!mediaPickerSetup.isStoragePermissionRequired || permissionsHandler.hasStoragePermission()) {
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
        var job: Job? = null
        job = viewModelScope.launch {
            var progressDialogJob: Job? = null
            mediaInsertHandler.insertMedia(ids).collect {
                when (it) {
                    is InsertModel.Progress -> {
                        progressDialogJob = launch {
                            delay(100)
                            _showProgressDialog.value = Visible(it.title) {
                                job?.cancel()
                                _showProgressDialog.value = Hidden
                            }
                        }
                    }
                    is InsertModel.Error -> {
                        val message = if (it.error.isNotEmpty()) {
                            UiString.UiStringText(
                                String.format(
                                    resourceProvider.getString(
                                        string.media_insert_failed_with_reason
                                    ),
                                    listOf(it.error)
                                )
                            )
                        } else {
                            UiStringRes(string.media_insert_failed)
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
                        if (_searchExpanded.value == true) {
                            _searchExpanded.value = false
                        }
                        _onNavigate.value = Event(InsertMedia(it.identifiers))
                    }
                }
            }
        }
    }

    fun onCameraPermissionsGranted() {
        startCamera()
    }

    private fun startCamera() {
        if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
            _onNavigate.value = Event(RequestCameraPermission)
            lastTappedIcon = CapturePhoto
            return
        }
        _onNavigate.postValue(Event(populateIconClickEvent(
            CapturePhoto,
            mediaPickerSetup.isMultiSelectEnabled
        )))
    }

    private fun startSystemPicker() {
        clickIcon(ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes))
    }

    private fun clickIcon(icon: MediaPickerIcon) {
        mediaPickerTracker.trackIconClick(icon, mediaPickerSetup)
        if (icon is CapturePhoto) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onNavigate.value = Event(RequestCameraPermission)
                lastTappedIcon = icon
                return
            }
        }
        _onNavigate.postValue(Event(populateIconClickEvent(
            icon,
            mediaPickerSetup.isMultiSelectEnabled
        )))
    }

    private fun clickOnCamera() {
        if (mediaPickerSetup.availableDataSources.contains(DataSource.CAMERA)) {
            clickIcon(CapturePhoto)
        }
    }

    private fun populateIconClickEvent(icon: MediaPickerIcon, canMultiselect: Boolean): IconClickEvent {
        val action: MediaPickerAction = when (icon) {
            is ChooseFromAndroidDevice -> {
                getSystemPickerAction(icon.allowedTypes, canMultiselect)
            }
            is CapturePhoto -> OpenCameraForPhotos
            is SwitchSource -> {
                val availableSources = mutableSetOf<DataSource>().apply {
                    if (icon.dataSource == DEVICE) add(SYSTEM_PICKER)
                }
                SwitchMediaPicker(
                        mediaPickerSetup.copy(
                                primaryDataSource = icon.dataSource,
                                availableDataSources = availableSources,
                                isSearchToggledByDefault = false
                        )
                )
            }
        }

        return IconClickEvent(action)
    }

    private fun getSystemPickerAction(
        allowedTypes: Set<MediaType>,
        canMultiselect: Boolean
    ): OpenSystemPicker {
        val (context, types) = when {
            listOf(IMAGE).containsAll(allowedTypes) -> {
                Pair(PHOTO, mimeTypeProvider.imageTypes)
            }
            listOf(VIDEO).containsAll(allowedTypes) -> {
                Pair(ChooserContext.VIDEO, mimeTypeProvider.videoTypes)
            }
            listOf(IMAGE, VIDEO).containsAll(allowedTypes) -> {
                Pair(
                    PHOTO_OR_VIDEO,
                    mimeTypeProvider.imageTypes + mimeTypeProvider.videoTypes
                )
            }
            listOf(AUDIO).containsAll(allowedTypes) -> {
                Pair(ChooserContext.AUDIO, mimeTypeProvider.audioTypes)
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

        if (permissionsHandler.hasStoragePermission()) {
            hideSoftRequest()
        } else {
            showSoftRequest(
                permission = STORAGE,
                isAlwaysDenied = isAlwaysDenied
            )
        }
    }

    fun checkCameraPermission(
        isCameraPermissionAlwaysDenied: Boolean,
        isStoragePermissionAlwaysDenied: Boolean
    ) {
        if (!permissionsHandler.hasCameraPermission()
            || !permissionsHandler.hasStoragePermission()) {
            val isAlwaysDenied = isCameraPermissionAlwaysDenied || isStoragePermissionAlwaysDenied
            showSoftRequest(
                permission = CAMERA,
                isAlwaysDenied = isAlwaysDenied
            )
        } else {
            hideSoftRequest()
        }
    }

    private fun hideSoftRequest() {
        _softAskRequest.value = SoftAskRequest(show = false)

        if (_domainModel.value?.domainItems.isNullOrEmpty()) {
            refreshData(false)
        }
    }

    private fun showSoftRequest(permission: PermissionsRequested, isAlwaysDenied: Boolean) {
        _softAskRequest.value = SoftAskRequest(
            show = true,
            type = permission,
            isAlwaysDenied = isAlwaysDenied,
            onClick = { onPermissionRequested(permission, isAlwaysDenied) }
        )
        _domainModel.value = _domainModel.value?.copy(isLoading = false, emptyState = null)
    }

    fun onMenuItemClicked(action: BrowseAction) {
        val icon = when (action) {
            BrowseAction.DEVICE -> SwitchSource(DEVICE)
            BrowseAction.SYSTEM_PICKER -> ChooseFromAndroidDevice(mediaPickerSetup.allowedTypes)
            BrowseAction.GIF_LIBRARY -> SwitchSource(GIF_LIBRARY)
            BrowseAction.WP_MEDIA_LIBRARY -> TODO()
            BrowseAction.STOCK_LIBRARY -> TODO()
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
            val label = if (softAskRequest.isAlwaysDenied) {
                val storage = permissionsHandler.getPermissionName(permission.READ_EXTERNAL_STORAGE)
                val camera = permissionsHandler.getPermissionName(permission.CAMERA)
                val permission = ("<strong>${
                    when (softAskRequest.type) {
                        STORAGE -> storage
                        CAMERA -> "$storage & $camera"
                    }
                }</strong>")
                String.format(
                    resourceProvider.getString(string.media_picker_soft_ask_permissions_denied),
                    permission
                )
            } else {
                when (softAskRequest.type) {
                    STORAGE -> resourceProvider.getString(string.photo_picker_soft_ask_label)
                    CAMERA -> resourceProvider.getString(string.camera_soft_ask_label)
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
                softAskRequest.onClick
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

    fun urisSelectedFromSystemPicker(uris: List<MediaUri>) {
        viewModelScope.launch {
            delay(100)
            insertIdentifiers(uris.map { LocalUri(it) })
        }
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
        val browseMenuUiModel: BrowseMenuUiModel,
        val progressDialogUiModel: ProgressDialogUiModel
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

    data class BrowseMenuUiModel(val shownActions: Set<BrowseAction>) {
        enum class BrowseAction {
            SYSTEM_PICKER, DEVICE, WP_MEDIA_LIBRARY, STOCK_LIBRARY, GIF_LIBRARY
        }
    }

    enum class PermissionsRequested {
        CAMERA, STORAGE
    }

    data class SoftAskRequest(
        val show: Boolean,
        val type: PermissionsRequested = STORAGE,
        val isAlwaysDenied: Boolean = false,
        val onClick: () -> Unit = {}
    )

    data class SnackbarMessageHolder(
        val message: UiString,
        val buttonTitle: UiString? = null,
        val buttonAction: () -> Unit = {},
        val onDismissAction: () -> Unit = {}
    )

    sealed class ProgressDialogUiModel {
        object Hidden : ProgressDialogUiModel()
        data class Visible(val title: Int, val cancelAction: () -> Unit) : ProgressDialogUiModel()
    }
}
