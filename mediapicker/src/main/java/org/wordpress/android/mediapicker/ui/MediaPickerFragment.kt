package org.wordpress.android.mediapicker.ui

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent.ACTION_GET_CONTENT
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Html
import android.view.*
import android.view.MenuItem.OnActionExpandListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
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
import org.wordpress.android.mediapicker.model.MediaPickerUiItem
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.*
import org.wordpress.android.mediapicker.model.MediaType
import org.wordpress.android.mediapicker.model.MediaUri
import org.wordpress.android.mediapicker.ui.MediaPickerFragment.MediaPickerIconType.*
import org.wordpress.android.mediapicker.util.*
import org.wordpress.android.mediapicker.util.AnimUtils.Duration.MEDIUM
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils.Companion.PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE
import org.wordpress.android.mediapicker.util.MediaPickerPermissionUtils.Companion.PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.BrowseMenuUiModel.BrowseAction.*
import org.wordpress.android.mediapicker.viewmodel.MediaPickerViewModel.PermissionsRequested.STORAGE
import org.wordpress.android.mediapicker.viewmodel.observeEvent
import javax.inject.Inject

@AndroidEntryPoint
class MediaPickerFragment : Fragment() {
    @Inject lateinit var log: Log

    enum class MediaPickerIconType {
        ANDROID_CHOOSE_FROM_DEVICE,
        SWITCH_SOURCE,
        CAPTURE_PHOTO;

        companion object {
            @JvmStatic
            fun fromNameString(iconTypeName: String): MediaPickerIconType {
                return values().firstOrNull { it.name == iconTypeName }
                        ?: throw IllegalArgumentException("MediaPickerIconType not found with name $iconTypeName")
            }
        }
    }

    enum class ChooserContext(
        val intentAction: String,
        val title: Int,
        val mediaTypeFilter: String
    ) {
        PHOTO(ACTION_GET_CONTENT,
            R.string.pick_photo, "image/*"),
        VIDEO(ACTION_GET_CONTENT,
            R.string.pick_video, "video/*"),
        PHOTO_OR_VIDEO(ACTION_GET_CONTENT,
            R.string.pick_media, "*/*"),
        AUDIO(ACTION_GET_CONTENT,
            R.string.pick_audio, "*/*"),
        MEDIA_FILE(ACTION_OPEN_DOCUMENT,
            R.string.pick_file, "*/*");
    }

    sealed class MediaPickerAction {
        data class OpenSystemPicker(
            val chooserContext: ChooserContext,
            val mimeTypes: List<String>,
            val allowMultipleSelection: Boolean
        ) : MediaPickerAction()

        object OpenCameraForPhotos : MediaPickerAction()
        data class SwitchMediaPicker(val mediaPickerSetup: MediaPickerSetup) : MediaPickerAction()
    }

    sealed class MediaPickerIcon(val type: MediaPickerIconType) {
        data class ChooseFromAndroidDevice(
            val allowedTypes: Set<MediaType>
        ) : MediaPickerIcon(ANDROID_CHOOSE_FROM_DEVICE)

        data class SwitchSource(val dataSource: DataSource) : MediaPickerIcon(SWITCH_SOURCE)

        object CapturePhoto : MediaPickerIcon(CAPTURE_PHOTO)

        fun toBundle(bundle: Bundle) {
            bundle.putString(KEY_LAST_TAPPED_ICON, type.name)
            when (this) {
                is ChooseFromAndroidDevice -> {
                    bundle.putStringArrayList(
                            KEY_LAST_TAPPED_ICON_ALLOWED_TYPES,
                            ArrayList(allowedTypes.map { it.name })
                    )
                }
                is SwitchSource -> {
                    bundle.putInt(KEY_LAST_TAPPED_ICON_DATA_SOURCE, this.dataSource.ordinal)
                }
            }
        }

        companion object {
            @JvmStatic
            fun fromBundle(bundle: Bundle): MediaPickerIcon? {
                val iconTypeName = bundle.getString(KEY_LAST_TAPPED_ICON) ?: return null

                return when (iconTypeName.let {
                    MediaPickerIconType.fromNameString(
                        iconTypeName
                    )
                }) {
                    ANDROID_CHOOSE_FROM_DEVICE -> {
                        val allowedTypes = (bundle.getStringArrayList(
                            KEY_LAST_TAPPED_ICON_ALLOWED_TYPES
                        )
                                ?: listOf<String>()).map {
                            MediaType.valueOf(it)
                        }.toSet()
                        ChooseFromAndroidDevice(allowedTypes)
                    }
                    CAPTURE_PHOTO -> CapturePhoto
                    SWITCH_SOURCE -> {
                        val ordinal = bundle.getInt(KEY_LAST_TAPPED_ICON_DATA_SOURCE, -1)
                        if (ordinal != -1) {
                            val dataSource = DataSource.values()[ordinal]
                            SwitchSource(dataSource)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    /*
     * parent activity must implement this listener
     */
    interface MediaPickerListener {
        fun onItemsChosen(identifiers: List<Identifier>)
        fun onIconClicked(action: MediaPickerAction)
    }

    private var listener: MediaPickerListener? = null

//    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var permissionUtils: MediaPickerPermissionUtils

    private val viewModel: MediaPickerViewModel by viewModels()
    private var binding: MediaPickerLibFragmentBinding? = null
    private lateinit var mediaPickerSetup: MediaPickerSetup

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaPickerSetup = MediaPickerSetup.fromBundle(requireArguments())
        var selectedIds: List<Identifier> = emptyList()
        var lastTappedIcon: MediaPickerIcon? = null
        if (savedInstanceState != null) {
            lastTappedIcon =
                MediaPickerIcon.fromBundle(
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
                        is InsertMedia -> listener?.onItemsChosen(navigationEvent.identifiers)
                        is IconClickEvent -> listener?.onIconClicked(navigationEvent.action)
                        Exit -> {
                            val activity = requireActivity()
                            activity.setResult(Activity.RESULT_CANCELED)
                            activity.finish()
                        }
                        RequestCameraPermission -> requestCameraPermissions()
                        RequestStoragePermission -> requestStoragePermission()
                        ShowAppSettings -> permissionUtils.showAppSettings(requireActivity())
                        is PreviewMedia -> TODO()
                    }
                })

            viewModel.onSnackbarMessage.observeEvent(viewLifecycleOwner, { messageHolder ->
                showSnackbar(messageHolder)
            })

            setupProgressDialog()

            viewModel.start(selectedIds, mediaPickerSetup, lastTappedIcon)
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

    fun urisSelectedFromSystemPicker(uris: List<Uri>) {
        viewModel.urisSelectedFromSystemPicker(uris.map {
            MediaUri(it.toString())
        })
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
        loadingView.visibility = if (uiModel == PhotoListUiModel.Loading) View.VISIBLE else View.GONE
        actionableEmptyView.visibility = if (uiModel is PhotoListUiModel.Empty) View.VISIBLE else View.GONE
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
        viewModel.lastTappedIcon?.toBundle(outState)
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

    fun setMediaPickerListener(listener: MediaPickerListener?) {
        this.listener = listener
    }

    private suspend fun isStoragePermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(
            requireActivity(), READ_EXTERNAL_STORAGE
        )
    }

    private suspend fun isCameraPermissionAlwaysDenied(): Boolean {
        return permissionUtils.isPermissionAlwaysDenied(
            requireActivity(), CAMERA
        )
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
            isCameraPermissionAlwaysDenied(),
            isStoragePermissionAlwaysDenied()
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
        // in addition to CAMERA permission we also need a storage permission,
        // to store media from the camera
        val permissions = arrayOf(CAMERA,  READ_EXTERNAL_STORAGE)
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

    companion object {
        private const val KEY_LAST_TAPPED_ICON = "last_tapped_icon"
        private const val KEY_LAST_TAPPED_ICON_ALLOWED_TYPES = "last_tapped_icon_allowed_types"
        private const val KEY_LAST_TAPPED_ICON_DATA_SOURCE = "last_tapped_icon_data_source"
        private const val KEY_SELECTED_IDS = "selected_ids"
        private const val KEY_LIST_STATE = "list_state"
        const val NUM_COLUMNS = 3
        @JvmStatic fun newInstance(
            listener: MediaPickerListener,
            mediaPickerSetup: MediaPickerSetup
        ): MediaPickerFragment {
            val args = Bundle()
            mediaPickerSetup.toBundle(args)
            val fragment = MediaPickerFragment()
            fragment.setMediaPickerListener(listener)
            fragment.arguments = args
            return fragment
        }
    }
}
