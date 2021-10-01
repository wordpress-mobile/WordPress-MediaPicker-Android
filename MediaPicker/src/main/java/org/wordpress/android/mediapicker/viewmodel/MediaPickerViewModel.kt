package org.wordpress.android.mediapicker.viewmodel

import android.Manifest.permission
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.wordpress.android.mediapicker.MediaNavigationEvent
import org.wordpress.android.mediapicker.MediaNavigationEvent.*
import org.wordpress.android.mediapicker.MediaPickerFragment.*
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerAction.*
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIcon.*
import org.wordpress.android.mediapicker.MediaPickerTracker
import org.wordpress.android.mediapicker.MediaPickerUiItem
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.MediaPickerUiItem.*
import org.wordpress.android.mediapicker.R.drawable
import org.wordpress.android.mediapicker.R.string
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.BrowseMenuUiModel.BrowseAction
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.ProgressDialogUiModel.Hidden
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.ProgressDialogUiModel.Visible
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MimeTypeSupportProvider
import org.wordpress.android.mediapicker.api.MediaInsertHandler
import org.wordpress.android.mediapicker.api.MediaInsertHandler.InsertModel
import org.wordpress.android.mediapicker.api.MediaInsertHandlerFactory
import org.wordpress.android.mediapicker.loader.MediaLoader
import org.wordpress.android.mediapicker.loader.MediaLoader.DomainModel
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction
import org.wordpress.android.mediapicker.loader.MediaLoader.LoadAction.NextPage
import org.wordpress.android.mediapicker.loader.MediaLoaderFactory
import org.wordpress.android.mediapicker.model.MediaItem
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
import org.wordpress.android.mediapicker.util.PermissionsHandler
import org.wordpress.android.mediapicker.util.UiString
import org.wordpress.android.mediapicker.util.distinct
import org.wordpress.android.mediapicker.util.merge
import javax.inject.Inject

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    private val mediaSourceFactory: MediaLoaderFactory,
    private val mediaInsertHandlerFactory: MediaInsertHandlerFactory,
    private val mediaPickerTracker: MediaPickerTracker,
    private val permissionsHandler: PermissionsHandler,
    private val resourceProvider: ResourceProvider,
    private val mimeTypeSupportProvider: MimeTypeSupportProvider
) : ViewModel() {
    private lateinit var mediaLoader: MediaLoader
    private lateinit var mediaInsertHandler: MediaInsertHandler
    private val loadActions = Channel<LoadAction>()
    private var searchJob: Job? = null
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
                buildUiModel(domainModel, selectedIds, softAskRequest, searchExpanded),
                buildSoftAskView(softAskRequest),
                FabUiModel(mediaPickerSetup.allowCameraCapture && selectedIds.isNullOrEmpty(), this::clickOnCamera),
                buildActionModeUiModel(selectedIds, domainModel?.domainItems),
                buildSearchUiModel(softAskRequest?.let { !it.show } ?: true, domainModel?.filter, searchExpanded),
                !domainModel?.domainItems.isNullOrEmpty() && domainModel?.isLoading == true,
                buildBrowseMenuUiModel(softAskRequest, searchExpanded),
                progressDialogUiModel ?: Hidden
        )
    }

    val selectedIdentifiers: List<Identifier>
        get() = _selectedIds.value ?: listOf()

    private fun buildSearchUiModel(isVisible: Boolean, filter: String?, searchExpanded: Boolean?): SearchUiModel {
        return when {
            searchExpanded == true -> Expanded(filter ?: "", !mediaPickerSetup.defaultSearchView)
            isVisible -> Collapsed
            else -> SearchUiModel.Hidden
        }
    }

    private fun buildBrowseMenuUiModel(softAskRequest: SoftAskRequest?, searchExpanded: Boolean?): BrowseMenuUiModel {
        val isSoftAskRequestVisible = softAskRequest?.show ?: false
        val isSearchExpanded = searchExpanded ?: false
        val showActions = !isSoftAskRequestVisible && !isSearchExpanded
        val showSystemPicker = mediaPickerSetup.isSystemPickerEnabled && showActions

        return if (showActions && (showSystemPicker || mediaPickerSetup.availableDataSources.isNotEmpty())) {
            val actions = mutableSetOf<BrowseAction>()
            if (showSystemPicker) {
                actions.add(BrowseAction.SYSTEM_PICKER)
            }
            actions.addAll(mediaPickerSetup.availableDataSources.map {
                when (it) {
                    DEVICE -> BrowseAction.DEVICE
                    GIF_LIBRARY -> BrowseAction.GIF_LIBRARY
                }
            })
            BrowseMenuUiModel(actions)
        } else {
            BrowseMenuUiModel(setOf())
        }
    }

    var lastTappedIcon: MediaPickerIcon? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup
    private var siteId: Long? = null

    private fun buildUiModel(
        domainModel: DomainModel?,
        selectedIds: List<Identifier>?,
        softAskRequest: SoftAskRequest?,
        isSearching: Boolean?
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
                    mimeTypeSupportProvider.getExtensionForMimeType(mimeType).uppercase()
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
        lastTappedIcon: MediaPickerIcon?,
        siteId: Long
    ) {
        _selectedIds.value = selectedIds
        this.mediaPickerSetup = mediaPickerSetup
        this.lastTappedIcon = lastTappedIcon
        this.siteId = siteId

        if (_domainModel.value == null) {
            mediaPickerTracker.trackMediaPickerOpened(mediaPickerSetup)

            this.mediaLoader = mediaSourceFactory.build(siteId, mediaPickerSetup)

            this.mediaInsertHandler = mediaInsertHandlerFactory.build(mediaPickerSetup)

            viewModelScope.launch {
                mediaLoader.loadMedia(loadActions).collect { domainModel ->
                    _domainModel.value = domainModel
                }
            }

            if (!mediaPickerSetup.requiresStoragePermissions || permissionsHandler.hasStoragePermission()) {
                viewModelScope.launch {
                    loadActions.send(LoadAction.Start())
                }
            }
        }
        if (mediaPickerSetup.defaultSearchView) {
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
                siteId?.let {
                    viewModelScope.launch {
                        _onNavigate.postValue(Event(PreviewMedia(identifier.value)))
                    }
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

    fun clickOnLastTappedIcon() = clickIcon(lastTappedIcon!!)

    private fun clickIcon(icon: MediaPickerIcon) {
        mediaPickerTracker.trackIconClick(icon, mediaPickerSetup)
        if (icon is CapturePhoto) {
            if (!permissionsHandler.hasPermissionsToAccessPhotos()) {
                _onPermissionsRequested.value = Event(CAMERA)
                lastTappedIcon = icon
                return
            }
        }
        _onNavigate.postValue(Event(populateIconClickEvent(icon, mediaPickerSetup.canMultiselect)))
    }

    private fun clickOnCamera() {
        if (mediaPickerSetup.allowCameraCapture) {
            clickIcon(CapturePhoto)
        }
    }

    private fun populateIconClickEvent(icon: MediaPickerIcon, canMultiselect: Boolean): IconClickEvent {
        val action: MediaPickerAction = when (icon) {
            is ChooseFromAndroidDevice -> {
                val allowedTypes = icon.allowedTypes
                val (context, types) = when {
                    listOf(IMAGE).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.PHOTO, mimeTypeSupportProvider.getImageTypesOnly())
                    }
                    listOf(VIDEO).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.VIDEO, mimeTypeSupportProvider.getVideoTypesOnly())
                    }
                    listOf(IMAGE, VIDEO).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.PHOTO_OR_VIDEO, mimeTypeSupportProvider.getVideoAndImagesTypes())
                    }
                    listOf(AUDIO).containsAll(allowedTypes) -> {
                        Pair(ChooserContext.AUDIO, mimeTypeSupportProvider.getAudioTypesOnly())
                    }
                    else -> {
                        Pair(ChooserContext.MEDIA_FILE, mimeTypeSupportProvider.getAllTypes())
                    }
                }
                OpenSystemPicker(context, types.toList(), canMultiselect)
            }
            is CapturePhoto -> OpenCameraForPhotos
            is SwitchSource -> {
                SwitchMediaPicker(
                        mediaPickerSetup.copy(
                                primaryDataSource = icon.dataSource,
                                availableDataSources = setOf(),
                                isSystemPickerEnabled = icon.dataSource == DEVICE,
                                defaultSearchView = false,
                                allowCameraCapture = false
                        )
                )
            }
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
            mediaPickerTracker.trackShowPermissionsScreen(mediaPickerSetup, softAskRequest.isAlwaysDenied)
            val label = if (softAskRequest.isAlwaysDenied) {
                val readPermission = ("<strong>${
                    permissionsHandler.getPermissionName(permission.READ_EXTERNAL_STORAGE)
                }</strong>")
                String.format(
                        resourceProvider.getString(string.media_picker_soft_ask_permissions_denied),
                        readPermission
                )
            } else {
                resourceProvider.getString(string.photo_picker_soft_ask_label)
            }
            val allowId = if (softAskRequest.isAlwaysDenied) {
                string.button_edit_permissions
            } else {
                string.photo_picker_soft_ask_allow
            }
            return SoftAskViewUiModel.Visible(
                label,
                UiStringRes(allowId),
                softAskRequest.isAlwaysDenied
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
        if (!mediaPickerSetup.defaultSearchView) {
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
            val isAlwaysDenied: Boolean
        ) :
                SoftAskViewUiModel()

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

    data class SoftAskRequest(val show: Boolean, val isAlwaysDenied: Boolean)

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
