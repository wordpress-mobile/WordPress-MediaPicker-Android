package org.wordpress.android.mediapicker.ui

import android.Manifest.permission.*
import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.text.Html
import android.view.*
import android.view.MenuItem.OnActionExpandListener
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.wordpress.android.mediapicker.R
import org.wordpress.android.mediapicker.model.MediaNavigationEvent.*
import org.wordpress.android.mediapicker.api.MediaPickerSetup.DataSource
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.ProgressDialogUiModel.Visible
import org.wordpress.android.mediapicker.api.MediaPickerSetup
import org.wordpress.android.mediapicker.databinding.MediaPickerLibFragmentBinding
import org.wordpress.android.mediapicker.model.MediaItem.Identifier
import org.wordpress.android.mediapicker.model.MediaPickerAction
import org.wordpress.android.mediapicker.model.MediaPickerAction.*
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.*
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.util.*
import org.wordpress.android.mediapicker.util.AnimUtils.Duration.MEDIUM
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils.Companion.PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils.Companion.PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.BrowseMenuUiModel.BrowseAction.*
import org.wordpress.android.mediapicker.viewmodel.observeEvent
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerFragment : Fragment() {
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

    private val switchSource = registerForActivityResult(StartActivityForResult()) {}

    private val systemPicker = registerForActivityResult(StartActivityForResult()) {
        handleSystemPickerResult(it)
    }

    private val camera = registerForActivityResult(StartActivityForResult()) {
        handleImageCaptureResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
            viewModel.uiState.observe(viewLifecycleOwner, {
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
            })

            viewModel.onNavigate.observeEvent(viewLifecycleOwner,
                { navigationEvent ->
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
                        ShowAppSettings -> permissionUtils.showAppSettings(requireActivity())
                        is PreviewMedia -> {
                        }
                    }
                })

            viewModel.onSnackbarMessage.observeEvent(viewLifecycleOwner, { messageHolder ->
                showSnackbar(messageHolder)
            })

            setupProgressDialog()

            requireActivity().actionBar?.setTitle(mediaPickerSetup.title)

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
                switchSource.launch(
                    MediaPickerActivity.buildIntent(
                        requireContext(),
                        action.mediaPickerSetup
                    )
                )
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
        viewModel.uiState.observe(viewLifecycleOwner, { uiState ->
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
        })
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
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                viewModel.onSearchExpanded()
                isExpanding = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
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
            softAskView.title.text = Html.fromHtml(softAskViewUiModel.label)
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
            if (uiModel == PhotoListUiModel.Loading) View.VISIBLE else View.GONE
        actionableEmptyView.visibility =
            if (uiModel is PhotoListUiModel.Empty) View.VISIBLE else View.GONE
        recycler.visibility = if (uiModel is PhotoListUiModel.Data) View.VISIBLE else View.INVISIBLE
        when (uiModel) {
            is PhotoListUiModel.Data -> {
                setupAdapter(uiModel.items)
            }
            is PhotoListUiModel.Empty -> {
                setupAdapter(listOf())
                actionableEmptyView.updateLayoutForSearch(uiModel.isSearching, 0)
                actionableEmptyView.title.text = UiHelpers.getTextOfUiString(
                    requireContext(),
                    uiModel.title
                )

                actionableEmptyView.subtitle.applyOrHide(uiModel.htmlSubtitle) { htmlSubtitle ->
                    actionableEmptyView.subtitle.text = Html.fromHtml(
                        UiHelpers.getTextOfUiString(
                            requireContext(),
                            htmlSubtitle
                        )
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

    private fun setupProgressDialog() {
        var progressDialog: AlertDialog? = null
        viewModel.uiState.observe(viewLifecycleOwner, Observer {
            it?.progressDialogUiModel?.apply {
                when (this) {
                    is Visible -> {
                        if (progressDialog == null || progressDialog?.isShowing == false) {
                            val builder: Builder = MaterialAlertDialogBuilder(requireContext())
                            builder.setTitle(this.title)
                            builder.setView(R.layout.media_picker_lib_progress_dialog)
                            builder.setNegativeButton(
                                R.string.cancel
                            ) { _, _ -> this.cancelAction() }
                            builder.setOnCancelListener { this.cancelAction() }
                            builder.setCancelable(true)
                            progressDialog = builder.show()
                        }
                    }
                    ProgressDialogUiModel.Hidden -> {
                        progressDialog?.let { dialog ->
                            if (dialog.isShowing) {
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun MediaPickerLibFragmentBinding.showSnackbar(holder: SnackbarMessageHolder) {
        val snackbar = Snackbar.make(
            requireContext(),
            coordinator,
            UiHelpers.getTextOfUiString(requireContext(), holder.message),
            Snackbar.LENGTH_LONG
        )
        if (holder.buttonTitle != null) {
            snackbar.setAction(
                UiHelpers.getTextOfUiString(requireContext(), holder.buttonTitle)
            ) { holder.buttonAction() }
        }
        snackbar.show()
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
                checkStoragePermission()
            }
        }
    }

    private suspend fun isStoragePermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(requireActivity(), READ_EXTERNAL_STORAGE)
    }

    private suspend fun isCameraPermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(requireActivity(), CAMERA)
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
            isCameraPermissionAlwaysDenied() || mediaPickerSetup.isStoragePermissionRequired
                    && isStoragePermissionAlwaysDenied()
        )
    }

    private fun requestStoragePermission() {
        val permissions = arrayOf(READ_EXTERNAL_STORAGE)
        requestPermissions(
            permissions,
            PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestCameraPermissions() {
        val permissions = if (mediaPickerSetup.isStoragePermissionRequired) {
            arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE)
        } else {
            arrayOf(CAMERA)
        }
        requestPermissions(
            permissions,
            PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE
        )
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        lifecycleScope.launch {
            val checkForAlwaysDenied = requestCode == PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE
            val allGranted = permissionUtils.setPermissionListAsked(
                requireActivity(), requestCode, permissions, grantResults, checkForAlwaysDenied
            )
            when (requestCode) {
                PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE -> checkStoragePermission()
                PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE -> {
                    if (allGranted) {
                        viewModel.onCameraPermissionsGranted()
                    } else {
                        checkCameraPermissions()
                    }
                }
            }
        }
    }
}

