package org.wordpress.android.mediapicker

import android.Manifest.permission
import android.app.Activity
import android.content.Intent.ACTION_GET_CONTENT
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.os.Bundle
import android.os.Parcelable
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.media_picker_fragment.*
import org.wordpress.android.R
import org.wordpress.android.R.layout
import org.wordpress.android.R.string
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.RequestCodes
import org.wordpress.android.ui.media.MediaPreviewActivity
import org.wordpress.android.mediapicker.MediaItem.Identifier
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIconType.ANDROID_CHOOSE_FROM_DEVICE
import org.wordpress.android.mediapicker.MediaPickerFragment.MediaPickerIconType.WP_STORIES_CAPTURE
import org.wordpress.android.mediapicker.MediaPickerViewModel.ActionModeUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.FabUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.MediaPickerViewModel.PermissionsRequested.STORAGE
import org.wordpress.android.mediapicker.MediaPickerViewModel.PhotoListUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.PhotoListUiModel.Data
import org.wordpress.android.mediapicker.MediaPickerViewModel.PhotoListUiModel.Empty
import org.wordpress.android.mediapicker.MediaPickerViewModel.PhotoListUiModel.Hidden
import org.wordpress.android.mediapicker.MediaPickerViewModel.ProgressDialogUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.ProgressDialogUiModel.Visible
import org.wordpress.android.mediapicker.MediaPickerViewModel.SearchUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.SoftAskViewUiModel
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.AccessibilityUtils
import org.wordpress.android.util.AniUtils
import org.wordpress.android.util.AniUtils.Duration.MEDIUM
import org.wordpress.android.util.SnackbarItem
import org.wordpress.android.util.SnackbarItem.Action
import org.wordpress.android.util.SnackbarItem.Info
import org.wordpress.android.util.SnackbarSequencer
import org.wordpress.android.util.WPLinkMovementMethod
import org.wordpress.android.util.WPMediaUtils
import org.wordpress.android.util.WPPermissionUtils
import org.wordpress.android.util.WPSwipeToRefreshHelper
import org.wordpress.android.util.config.TenorFeatureConfig
import org.wordpress.android.util.image.ImageManager
import javax.inject.Inject

class MediaPickerFragment : Fragment() {
    enum class MediaPickerIconType {
        ANDROID_CHOOSE_FROM_DEVICE,
        WP_STORIES_CAPTURE;

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
        val requestCode: Int,
        val title: UiStringRes,
        val mediaTypeFilter: String
    ) {
        PHOTO(ACTION_GET_CONTENT, RequestCodes.PICTURE_LIBRARY, UiStringRes(R.string.pick_photo), "image/*"),
        VIDEO(ACTION_GET_CONTENT, RequestCodes.VIDEO_LIBRARY, UiStringRes(R.string.pick_video), "video/*"),
        PHOTO_OR_VIDEO(ACTION_GET_CONTENT, RequestCodes.MEDIA_LIBRARY, UiStringRes(R.string.pick_media), "*/*"),
        MEDIA_FILE(ACTION_OPEN_DOCUMENT, RequestCodes.FILE_LIBRARY, UiStringRes(R.string.pick_file), "*/*");
    }

    sealed class MediaPickerAction {
        data class OpenSystemPicker(
            val chooserContext: ChooserContext,
            val mimeTypes: List<String>,
            val allowMultipleSelection: Boolean
        ) : MediaPickerAction()

        data class OpenCameraForWPStories(val allowMultipleSelection: Boolean) : MediaPickerAction()
    }

    sealed class MediaPickerIcon(val type: MediaPickerIconType) {
        data class ChooseFromAndroidDevice(
            val allowedTypes: Set<MediaType>
        ) : MediaPickerIcon(ANDROID_CHOOSE_FROM_DEVICE)

        object WpStoriesCapture : MediaPickerIcon(WP_STORIES_CAPTURE)

        fun toBundle(bundle: Bundle) {
            bundle.putString(KEY_LAST_TAPPED_ICON, type.name)
            if (this is ChooseFromAndroidDevice) {
                bundle.putStringArrayList(KEY_LAST_TAPPED_ICON_ALLOWED_TYPES, ArrayList(allowedTypes.map { it.name }))
            }
        }

        companion object {
            @JvmStatic
            fun fromBundle(bundle: Bundle): MediaPickerIcon? {
                val iconTypeName = bundle.getString(KEY_LAST_TAPPED_ICON) ?: return null

                return when (iconTypeName.let { MediaPickerIconType.fromNameString(iconTypeName) }) {
                    ANDROID_CHOOSE_FROM_DEVICE -> {
                        val allowedTypes = (bundle.getStringArrayList(KEY_LAST_TAPPED_ICON_ALLOWED_TYPES)
                                ?: listOf<String>()).map {
                            MediaType.valueOf(
                                    it
                            )
                        }.toSet()
                        ChooseFromAndroidDevice(allowedTypes)
                    }
                    WP_STORIES_CAPTURE -> WpStoriesCapture
                }
            }
        }
    }

    /*
     * parent activity must implement this listener
     */
    interface MediaPickerListener {
        fun onItemsChosen(uriList: List<Identifier>)
        fun onIconClicked(action: MediaPickerAction)
    }

    private var listener: MediaPickerListener? = null

    @Inject lateinit var tenorFeatureConfig: TenorFeatureConfig
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var snackbarSequencer: SnackbarSequencer
    @Inject lateinit var uiHelpers: UiHelpers
    private lateinit var viewModel: MediaPickerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as WordPress).component().inject(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(MediaPickerViewModel::class.java)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
                R.layout.media_picker_fragment,
                container,
                false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mediaPickerSetup = MediaPickerSetup.fromBundle(requireArguments())
        val site = requireArguments().getSerializable(WordPress.SITE) as? SiteModel
        var selectedIds: List<Identifier>? = null
        var lastTappedIcon: MediaPickerIcon? = null
        if (savedInstanceState != null) {
            lastTappedIcon = MediaPickerIcon.fromBundle(savedInstanceState)
            if (savedInstanceState.containsKey(KEY_SELECTED_IDS)) {
                selectedIds = savedInstanceState.getParcelableArrayList<Identifier.Parcel>(KEY_SELECTED_IDS)
                        ?.map { Identifier.fromParcel(it) }
            }
        }

        val layoutManager = GridLayoutManager(
                activity,
                NUM_COLUMNS
        )

        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        recycler.layoutManager = layoutManager
        recycler.setEmptyView(actionable_empty_view)
        recycler.setHasFixedSize(true)

        val swipeToRefreshHelper = WPSwipeToRefreshHelper.buildSwipeToRefreshHelper(pullToRefresh) {
            viewModel.onPullToRefresh()
        }

        var isShowingActionMode = false
        viewModel.uiState.observe(viewLifecycleOwner, Observer {
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
                uiState.fabUiModel.let(this::setupFab)
                swipeToRefreshHelper.isRefreshing = uiState.isRefreshing
            }
        })

        viewModel.onNavigateToPreview.observe(viewLifecycleOwner, Observer
        {
            it.getContentIfNotHandled()?.let { uri ->
                MediaPreviewActivity.showPreview(
                        requireContext(),
                        null,
                        uri.toString()
                )
                AccessibilityUtils.setActionModeDoneButtonContentDescription(activity, getString(R.string.cancel))
            }
        })

        viewModel.onNavigateToEdit.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let { uris ->
                val inputData = WPMediaUtils.createListOfEditImageInputData(
                        requireContext(),
                        uris.map { wrapper -> wrapper.uri }
                )
                ActivityLauncher.openImageEditor(activity, inputData)
            }
        })

        viewModel.onInsert.observe(viewLifecycleOwner, Observer
        { event ->
            event.getContentIfNotHandled()?.let { selectedIds ->
                listener?.onItemsChosen(selectedIds)
            }
        })

        viewModel.onIconClicked.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { (action) ->
                listener?.onIconClicked(action)
            }
        })

        viewModel.onPermissionsRequested.observe(viewLifecycleOwner, Observer {
            it?.applyIfNotHandled {
                when (this) {
                    CAMERA -> requestCameraPermission()
                    STORAGE -> requestStoragePermission()
                }
            }
        })
        viewModel.onExit.observe(viewLifecycleOwner, Observer {
            it?.applyIfNotHandled {
                val activity = requireActivity()
                activity.setResult(Activity.RESULT_CANCELED)
                activity.finish()
            }
        })
        viewModel.onSnackbarMessage.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { messageHolder ->
                showSnackbar(messageHolder)
            }
        })
        setupProgressDialog()

        viewModel.start(selectedIds, mediaPickerSetup, lastTappedIcon, site)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_media_picker, menu)

        val searchMenuItem = checkNotNull(menu.findItem(R.id.action_search)) {
            "Menu does not contain mandatory search item"
        }
        val browseMenuItem = checkNotNull(menu.findItem(R.id.mnu_browse_item)) {
            "Menu does not contain mandatory browse item"
        }
        initializeSearchView(searchMenuItem)
        viewModel.uiState.observe(viewLifecycleOwner, Observer { uiState ->
            val searchView = searchMenuItem.actionView as SearchView

            if (uiState.searchUiModel is SearchUiModel.Expanded && !searchMenuItem.isActionViewExpanded) {
                searchMenuItem.expandActionView()
                searchView.setQuery(uiState.searchUiModel.filter, true)
                searchView.setOnCloseListener { !uiState.searchUiModel.closeable }
            } else if (uiState.searchUiModel is SearchUiModel.Collapsed && searchMenuItem.isActionViewExpanded) {
                searchMenuItem.collapseActionView()
            }

            searchMenuItem.isVisible = uiState.searchUiModel !is SearchUiModel.Hidden
            browseMenuItem.isVisible = uiState.browseMenuUiModel.isVisible
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.mnu_browse_item) {
            viewModel.onBrowseForItems()
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

    private fun setupSoftAskView(uiModel: SoftAskViewUiModel) {
        when (uiModel) {
            is SoftAskViewUiModel.Visible -> {
                soft_ask_view.title.text = Html.fromHtml(uiModel.label)
                soft_ask_view.button.setText(uiModel.allowId.stringRes)
                soft_ask_view.button.setOnClickListener {
                    if (uiModel.isAlwaysDenied) {
                        WPPermissionUtils.showAppSettings(requireActivity())
                    } else {
                        requestStoragePermission()
                    }
                }

                soft_ask_view.visibility = View.VISIBLE
            }
            is SoftAskViewUiModel.Hidden -> {
                if (soft_ask_view.visibility == View.VISIBLE) {
                    AniUtils.fadeOut(soft_ask_view, MEDIUM)
                }
            }
        }
    }

    private fun setupPhotoList(uiModel: PhotoListUiModel) {
        when (uiModel) {
            is Data -> {
                actionable_empty_view.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                setupAdapter(uiModel.items)
            }
            is Empty -> {
                actionable_empty_view.visibility = View.VISIBLE
                actionable_empty_view.title.text = uiHelpers.getTextOfUiString(requireContext(), uiModel.title)
                if (uiModel.htmlSubtitle != null) {
                    actionable_empty_view.subtitle.text = Html.fromHtml(
                            uiHelpers.getTextOfUiString(
                                    requireContext(),
                                    uiModel.htmlSubtitle
                            ).toString()
                    )
                    actionable_empty_view.subtitle.movementMethod = WPLinkMovementMethod.getInstance()
                    actionable_empty_view.subtitle.visibility = View.VISIBLE
                } else {
                    actionable_empty_view.subtitle.visibility = View.GONE
                }

                if (uiModel.image != null) {
                    actionable_empty_view.image.setImageResource(uiModel.image)
                    actionable_empty_view.image.visibility = View.VISIBLE
                } else {
                    actionable_empty_view.image.visibility = View.GONE
                }

                recycler.visibility = View.INVISIBLE
                setupAdapter(listOf())
            }
            Hidden -> {
                actionable_empty_view.visibility = View.GONE
                recycler.visibility = View.INVISIBLE
            }
        }
    }

    private fun setupAdapter(items: List<MediaPickerUiItem>) {
        if (recycler.adapter == null) {
            recycler.adapter = MediaPickerAdapter(
                    imageManager
            )
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
        adapter.loadData(items)
    }

    private fun setupFab(fabUiModel: FabUiModel) {
        if (fabUiModel.show) {
            wp_stories_take_picture.show()
            wp_stories_take_picture.setOnClickListener {
                fabUiModel.action()
            }
        } else {
            wp_stories_take_picture.hide()
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
                            builder.setTitle(string.media_uploading_stock_library_photo)
                            builder.setView(layout.media_picker_progress_dialog)
                            builder.setNegativeButton(
                                    string.cancel
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

    private fun showSnackbar(holder: SnackbarMessageHolder) {
        snackbarSequencer.enqueue(
                SnackbarItem(
                        Info(
                                view = coordinator,
                                textRes = holder.message,
                                duration = Snackbar.LENGTH_LONG
                        ),
                        holder.buttonTitle?.let {
                            Action(
                                    textRes = holder.buttonTitle,
                                    clickListener = View.OnClickListener { holder.buttonAction() }
                            )
                        },
                        dismissCallback = { _, _ -> holder.onDismissAction() }
                )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.lastTappedIcon?.toBundle(outState)
        val selectedIds = viewModel.selectedIdentifiers().map { it.toParcel() }
        if (selectedIds.isNotEmpty()) {
            outState.putParcelableArrayList(KEY_SELECTED_IDS, ArrayList(selectedIds))
        }
        recycler.layoutManager?.let {
            outState.putParcelable(KEY_LIST_STATE, it.onSaveInstanceState())
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
    }

    fun setMediaPickerListener(listener: MediaPickerListener?) {
        this.listener = listener
    }

    private val isStoragePermissionAlwaysDenied: Boolean
        get() = WPPermissionUtils.isPermissionAlwaysDenied(
                requireActivity(), permission.WRITE_EXTERNAL_STORAGE
        )

    /*
     * load the photos if we have the necessary permission, otherwise show the "soft ask" view
     * which asks the user to allow the permission
     */
    private fun checkStoragePermission() {
        if (!isAdded) {
            return
        }
        viewModel.checkStoragePermission(isStoragePermissionAlwaysDenied)
    }

    private fun requestStoragePermission() {
        val permissions = arrayOf(permission.WRITE_EXTERNAL_STORAGE, permission.READ_EXTERNAL_STORAGE)
        requestPermissions(
                permissions, WPPermissionUtils.PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestCameraPermission() {
        // in addition to CAMERA permission we also need a storage permission, to store media from the camera
        val permissions = arrayOf(
                permission.CAMERA,
                permission.WRITE_EXTERNAL_STORAGE
        )
        requestPermissions(permissions, WPPermissionUtils.PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val checkForAlwaysDenied = requestCode == WPPermissionUtils.PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE
        val allGranted = WPPermissionUtils.setPermissionListAsked(
                requireActivity(), requestCode, permissions, grantResults, checkForAlwaysDenied
        )
        when (requestCode) {
            WPPermissionUtils.PHOTO_PICKER_STORAGE_PERMISSION_REQUEST_CODE -> checkStoragePermission()
            WPPermissionUtils.PHOTO_PICKER_CAMERA_PERMISSION_REQUEST_CODE -> if (allGranted) {
                viewModel.clickOnLastTappedIcon()
            }
        }
    }

    companion object {
        private const val KEY_LAST_TAPPED_ICON = "last_tapped_icon"
        private const val KEY_LAST_TAPPED_ICON_ALLOWED_TYPES = "last_tapped_icon_allowed_types"
        private const val KEY_SELECTED_IDS = "selected_ids"
        private const val KEY_LIST_STATE = "list_state"
        const val NUM_COLUMNS = 3
        @JvmStatic fun newInstance(
            listener: MediaPickerListener,
            mediaPickerSetup: MediaPickerSetup,
            site: SiteModel?
        ): MediaPickerFragment {
            val args = Bundle()
            mediaPickerSetup.toBundle(args)
            if (site != null) {
                args.putSerializable(WordPress.SITE, site)
            }
            val fragment = MediaPickerFragment()
            fragment.setMediaPickerListener(listener)
            fragment.arguments = args
            return fragment
        }
    }
}
