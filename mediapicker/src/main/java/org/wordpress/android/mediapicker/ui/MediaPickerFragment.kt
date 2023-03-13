package org.wordpress.android.mediapicker.ui

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.wordpress.android.mediapicker.MediaPickerUtils
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.api.Log
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource.SYSTEM_PICKER
import org.wordpress.android.mediapicker.databinding.MediaPickerLibFragmentBinding
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
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
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.model.UiStateModels.ActionModeUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.FabUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested
import org.wordpress.android.mediapicker.model.UiStateModels.PermissionsRequested.Companion
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Data
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Empty
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Hidden
import org.wordpress.android.mediapicker.model.UiStateModels.PhotoListUiModel.Loading
import org.wordpress.android.mediapicker.model.UiStateModels.SearchUiModel
import org.wordpress.android.mediapicker.model.UiStateModels.SoftAskViewUiModel
import org.wordpress.android.mediapicker.util.AnimUtils
import org.wordpress.android.mediapicker.util.AnimUtils.Duration.MEDIUM
import org.wordpress.android.mediapicker.util.MediaPickerLinkMovementMethod
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils
import org.wordpress.android.mediapicker.util.MediaUtils
import org.wordpress.android.mediapicker.util.UiHelpers
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel
import org.wordpress.android.mediapicker.viewmodel.observeEvent
import javax.inject.Inject

@AndroidEntryPoint
internal class MediaPickerFragment : Fragment() {
    companion object {
        private const val KEY_SELECTED_IDS = "selected_ids"
        private const val KEY_LIST_STATE = "list_state"
        const val NUM_COLUMNS = 3
    }

    @Inject
    lateinit var log: Log
    @Inject
    lateinit var mediaPickerUtils: MediaPickerUtils
    @Inject
    lateinit var permissionUtils: MediaPickerPermissionUtils

    private val viewModel: MediaPickerViewModel by viewModels()
    private var binding: MediaPickerLibFragmentBinding? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

    private val systemPicker = registerForActivityResult(StartActivityForResult()) {
        handleSystemPickerResult(it)
    }

    private val camera = registerForActivityResult(StartActivityForResult()) {
        handleImageCaptureResult(it)
    }

    private val storagePermissionRequest = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        lifecycleScope.launch {
            permissionUtils.persistPermissionRequestResults(permissions)
            checkStoragePermission()
        }
    }

    private val cameraPermissionRequest = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        lifecycleScope.launch {
            val allGranted = permissions.values.all { it }
            permissionUtils.persistPermissionRequestResults(permissions)
            if (allGranted) {
                viewModel.onCameraPermissionsGranted()
            } else {
                checkCameraPermissions()
            }
        }
    }

    private val mediaPermissionRequest = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions ->
        lifecycleScope.launch {
            permissionUtils.persistPermissionRequestResults(permissions)
            checkMediaPermissions(permissions.keys.map { PermissionsRequested.fromString(it) })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.media_picker_lib_fragment,
            container,
            false
        )
    }

    private fun handleSystemPickerResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val uris = MediaUtils.retrieveMediaUris(data).map { MediaUri(it.toString()) }
                viewModel.onUrisSelectedFromSystemPicker(uris)
            }
        } else {
            viewModel.onMediaPickerActionFailed()
        }
    }

    private fun handleImageCaptureResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onImageCaptured()
        } else {
            viewModel.onMediaPickerActionFailed()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaPickerSetup = if (savedInstanceState == null) {
            MediaPickerSetup.fromIntent(requireActivity().intent)
        } else {
            MediaPickerSetup.fromBundle(savedInstanceState)
        }

        var selectedIds: List<Identifier> = emptyList()
        var lastTappedAction: MediaPickerActionEvent? = null
        if (savedInstanceState != null) {
            lastTappedAction =
                MediaPickerActionEvent.fromBundle(
                    savedInstanceState
                )
            if (savedInstanceState.containsKey(KEY_SELECTED_IDS)) {
                selectedIds = savedInstanceState.getParcelableArrayList<Identifier>(
                    KEY_SELECTED_IDS
                )?.map { it } ?: emptyList()
            }
        }

        val layoutManager = GridLayoutManager(
            activity,
            NUM_COLUMNS
        )

        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        with(MediaPickerLibFragmentBinding.bind(view)) {
            binding = this
            recycler.layoutManager = layoutManager
            recycler.setEmptyView(actionableEmptyView)
            recycler.setHasFixedSize(true)

            pullToRefresh.apply {
                setOnRefreshListener {
                    viewModel.onPullToRefresh()
                }
            }

            var isShowingActionMode = false
            viewModel.uiState.observe(viewLifecycleOwner) {
                it?.let { uiState ->
                    setupPhotoList(uiState.photoListUiModel)
                    setupSoftAskView(uiState.softAskViewUiModel)
                    if (uiState.actionModeUiModel is ActionModeUiModel.Visible && !isShowingActionMode) {
                        isShowingActionMode = true
                        (activity as AppCompatActivity).startSupportActionMode(
                            MediaPickerActionModeCallback(
                                viewModel
                            )
                        )
                    } else if (uiState.actionModeUiModel is ActionModeUiModel.Hidden && isShowingActionMode) {
                        isShowingActionMode = false
                    }
                    setupFab(uiState.fabUiModel)
                    pullToRefresh.isRefreshing = uiState.isRefreshing
                }
            }

            viewModel.onNavigate.observeEvent(viewLifecycleOwner) { navigationEvent ->
                when (navigationEvent) {
                    is PreviewUrl -> {
                        MediaViewerFragment.previewUrl(
                            requireActivity(),
                            navigationEvent.url
                        )
                    }
                    is ReturnSelectedMedia -> {
                        val resultIntent = ResultIntentHelper.getSelectedMediaResultIntent(
                            navigationEvent.identifiers,
                            mediaPickerSetup.primaryDataSource
                        )
                        requireActivity().apply {
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                    is ReturnCapturedImage -> {
                        val resultIntent = ResultIntentHelper.getCapturedImageResultIntent(
                            navigationEvent.areResultsQueued,
                            navigationEvent.capturedImageUri
                        )
                        requireActivity().apply {
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                    is ChooseMediaPickerAction -> onActionSelected(navigationEvent.action)
                    Exit -> {
                        requireActivity().apply {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    }
                    RequestCameraPermission -> requestCameraPermissions()
                    RequestStoragePermission -> requestStoragePermission()
                    is RequestMediaPermissions -> requestMediaPermissions(navigationEvent.permissions)
                    ShowAppSettings -> permissionUtils.showAppSettings(requireActivity())
                }
            }

            (requireActivity() as AppCompatActivity).supportActionBar
                ?.setTitle(mediaPickerSetup.title)

            viewModel.start(selectedIds, mediaPickerSetup, lastTappedAction)
        }
    }

    private fun onActionSelected(action: MediaPickerAction) {
        when (action) {
            is OpenSystemPicker -> {
                val systemPickerIntent = mediaPickerUtils.createSystemPickerIntent(action)
                systemPicker.launch(systemPickerIntent)
            }
            is SwitchMediaPicker -> {
                mediaPickerSetup = action.mediaPickerSetup
                viewModel.restart(action.mediaPickerSetup)
                checkPermissions()
            }
            is OpenCameraForPhotos -> {
                action.imagePath?.let {
                    camera.launch(mediaPickerUtils.createCaptureImageIntent(it))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.media_picker_lib_menu, menu)

        val searchMenuItem = checkNotNull(menu.findItem(R.id.action_search)) {
            "Menu does not contain mandatory search item"
        }
        val browseMenuItem = checkNotNull(menu.findItem(R.id.mnu_browse_item)) {
            "Menu does not contain mandatory browse item"
        }
        val deviceMenuItem = checkNotNull(menu.findItem(R.id.mnu_choose_from_device)) {
            "Menu does not contain device library item"
        }
        val tenorLibraryMenuItem = checkNotNull(menu.findItem(R.id.mnu_choose_from_tenor_library)) {
            "Menu does not contain mandatory tenor library item"
        }

        initializeSearchView(searchMenuItem)
        viewModel.uiState.observe(viewLifecycleOwner) { uiState ->
            val searchView = searchMenuItem.actionView as SearchView

            if (uiState.searchUiModel is SearchUiModel.Expanded && !searchMenuItem.isActionViewExpanded) {
                searchMenuItem.expandActionView()
                searchView.maxWidth = Integer.MAX_VALUE
                searchView.setQuery(uiState.searchUiModel.filter, true)
                searchView.setOnCloseListener { !uiState.searchUiModel.closeable }
            } else if (uiState.searchUiModel is SearchUiModel.Collapsed && searchMenuItem.isActionViewExpanded) {
                searchMenuItem.collapseActionView()
            }

            searchMenuItem.isVisible = uiState.searchUiModel !is SearchUiModel.Hidden

            val shownActions = uiState.browseMenuUiModel.shownActions
            browseMenuItem.isVisible = shownActions.contains(SYSTEM_PICKER)
            deviceMenuItem.isVisible = shownActions.contains(DEVICE)
            tenorLibraryMenuItem.isVisible = shownActions.contains(GIF_LIBRARY)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mnu_browse_item -> {
                viewModel.onMenuItemClicked(SYSTEM_PICKER)
            }
            R.id.mnu_choose_from_device -> {
                viewModel.onMenuItemClicked(DEVICE)
            }
            R.id.mnu_choose_from_tenor_library -> {
                viewModel.onMenuItemClicked(GIF_LIBRARY)
            }
        }
        return true
    }

    private fun initializeSearchView(actionMenuItem: MenuItem) {
        var isExpanding = false
        actionMenuItem.setOnActionExpandListener(object : OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                viewModel.onSearchExpanded()
                isExpanding = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.onSearchCollapsed()
                return true
            }
        })
        val searchView = actionMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.onSearch(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (!isExpanding) {
                    viewModel.onSearch(query)
                }
                isExpanding = false
                return true
            }
        })
    }

    private fun MediaPickerLibFragmentBinding.setupSoftAskView(
        softAskViewUiModel: SoftAskViewUiModel,
    ) {
        if (softAskViewUiModel is SoftAskViewUiModel.Visible) {
            softAskView.title.text = HtmlCompat.fromHtml(
                softAskViewUiModel.label,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            softAskView.button.setText(softAskViewUiModel.allowId.stringRes)
            softAskView.button.setOnClickListener {
                softAskViewUiModel.onClick()
            }

            softAskView.visibility = View.VISIBLE
        } else {
            if (softAskView.visibility == View.VISIBLE) {
                AnimUtils.fadeOut(softAskView, MEDIUM)
            }
        }
    }

    private fun MediaPickerLibFragmentBinding.setupPhotoList(uiModel: PhotoListUiModel) {
        loadingView.visibility =
            if (uiModel == Loading) View.VISIBLE else View.GONE
        actionableEmptyView.visibility =
            if (uiModel is Empty) View.VISIBLE else View.GONE
        recycler.visibility = if (uiModel is Data) View.VISIBLE else View.INVISIBLE
        when (uiModel) {
            is Data -> {
                setupAdapter(uiModel.items)
            }
            is Empty -> {
                setupAdapter(listOf())
                actionableEmptyView.updateLayoutForSearch(uiModel.isSearching, 0)
                actionableEmptyView.title.text = UiHelpers.getTextOfUiString(
                    requireContext(),
                    uiModel.title
                )

                actionableEmptyView.subtitle.applyOrHide(uiModel.htmlSubtitle) { htmlSubtitle ->
                    actionableEmptyView.subtitle.text = HtmlCompat.fromHtml(
                        UiHelpers.getTextOfUiString(
                            requireContext(),
                            htmlSubtitle
                        ),
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                    actionableEmptyView.subtitle.movementMethod =
                        MediaPickerLinkMovementMethod.getInstance(log)
                }
                actionableEmptyView.image.applyOrHide(uiModel.image) { image ->
                    this.setImageResource(image)
                }
                actionableEmptyView.bottomImage.applyOrHide(uiModel.bottomImage) { bottomImage ->
                    this.setImageResource(bottomImage)
                    if (uiModel.bottomImageDescription != null) {
                        this.contentDescription = UiHelpers.getTextOfUiString(
                            requireContext(),
                            uiModel.bottomImageDescription
                        )
                    }
                }
                actionableEmptyView.button.isVisible = uiModel.retryAction != null
                actionableEmptyView.setOnClickListener {
                    uiModel.retryAction?.invoke()
                }
            }
            Hidden -> {}
            Loading -> {}
        }
    }

    private fun <T, U : View> U.applyOrHide(item: T?, action: U.(T) -> Unit) {
        if (item != null) {
            this.visibility = View.VISIBLE
            this.action(item)
        } else {
            this.visibility = View.GONE
        }
    }

    private fun MediaPickerLibFragmentBinding.setupAdapter(items: List<MediaPickerUiItem>) {
        if (recycler.adapter == null) {
            recycler.adapter = MediaPickerAdapter(lifecycleScope)
        }
        val adapter = recycler.adapter as MediaPickerAdapter

        (recycler.layoutManager as? GridLayoutManager)?.spanSizeLookup =
            object : SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (items[position].fullWidthItem) {
                    NUM_COLUMNS
                } else {
                    1
                }
            }
        val recyclerViewState = recycler.layoutManager?.onSaveInstanceState()
        adapter.loadData(items)
        recycler.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private fun MediaPickerLibFragmentBinding.setupFab(fabUiModel: FabUiModel) {
        if (fabUiModel.show) {
            fabTakePicture.show()
            fabTakePicture.setOnClickListener {
                fabUiModel.action()
            }
        } else {
            fabTakePicture.hide()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        viewModel.lastTappedAction?.toBundle(outState)
        val selectedIds = viewModel.selectedIdentifiers
        if (selectedIds.isNotEmpty()) {
            outState.putParcelableArrayList(
                KEY_SELECTED_IDS,
                ArrayList<Identifier>(selectedIds)
            )
        }
        binding!!.recycler.layoutManager?.let {
            outState.putParcelable(KEY_LIST_STATE, it.onSaveInstanceState())
        }

        mediaPickerSetup.toBundle(outState)
    }

    override fun onResume() {
        super.onResume()

        checkPermissions()
    }

    private fun checkPermissions() {
        lifecycleScope.launch {
            if (mediaPickerSetup.primaryDataSource == DataSource.CAMERA) {
                checkCameraPermissions()
            } else {
                if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                    checkMediaPermissions(
                        mediaPickerSetup.allowedTypes.map {
                            PermissionsRequested.fromMediaType(it)
                        }
                    )
                } else {
                    checkStoragePermission()
                }
            }
        }
    }

    private suspend fun isStoragePermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(requireActivity(), READ_EXTERNAL_STORAGE)
    }

    private suspend fun isCameraPermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(requireActivity(), CAMERA)
    }

    private suspend fun isMediaPermissionAlwaysDenied(permissions: List<PermissionsRequested>): Boolean {
        return permissions.any { permission ->
            permissionUtils.isPermissionAlwaysDenied(requireActivity(), permission.toString())
        }
    }

    /*
     * load the photos if we have the necessary permission, otherwise show the "soft ask" view
     * which asks the user to allow the permission
     */
    private suspend fun checkStoragePermission() {
        if (!isAdded) {
            return
        }
        viewModel.checkStoragePermission(isStoragePermissionAlwaysDenied())
    }

    private suspend fun checkCameraPermissions() {
        if (!isAdded) {
            return
        }
        viewModel.checkCameraPermission(
            isCameraPermissionAlwaysDenied() || mediaPickerSetup.isStoragePermissionRequired &&
                isStoragePermissionAlwaysDenied()
        )
    }

    private suspend fun checkMediaPermissions(permissions: List<PermissionsRequested>) {
        if (!isAdded) {
            return
        }
        viewModel.checkMediaPermissions(
            permissions = permissions,
            isAlwaysDenied = isMediaPermissionAlwaysDenied(permissions)
        )
    }

    private fun requestStoragePermission() {
        storagePermissionRequest.launch(arrayOf(READ_EXTERNAL_STORAGE))
    }

    private fun requestCameraPermissions() {
        val permissions = if (mediaPickerSetup.isStoragePermissionRequired) {
            arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(CAMERA)
        }
        cameraPermissionRequest.launch(permissions)
    }

    private fun requestMediaPermissions(permissions: List<PermissionsRequested>) {
        mediaPermissionRequest.launch(permissions.map { it.toString() }.toTypedArray())
    }
}
