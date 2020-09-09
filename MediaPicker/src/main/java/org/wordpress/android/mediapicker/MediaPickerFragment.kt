package org.wordpress.android.mediapicker

import android.Manifest.permission
import android.net.Uri
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.media_picker_fragment.*
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.media.MediaPreviewActivity
import org.wordpress.android.mediapicker.MediaPickerViewModel.ActionModeUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.FabUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.PermissionsRequested.CAMERA
import org.wordpress.android.mediapicker.MediaPickerViewModel.PermissionsRequested.STORAGE
import org.wordpress.android.mediapicker.MediaPickerViewModel.PhotoListUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.SearchUiModel
import org.wordpress.android.mediapicker.MediaPickerViewModel.SoftAskViewUiModel
import org.wordpress.android.util.AccessibilityUtils
import org.wordpress.android.util.AniUtils
import org.wordpress.android.util.AniUtils.Duration.MEDIUM
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.POSTS
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.util.WPMediaUtils
import org.wordpress.android.util.WPPermissionUtils
import org.wordpress.android.util.config.TenorFeatureConfig
import org.wordpress.android.util.image.ImageManager
import javax.inject.Inject

class MediaPickerFragment : Fragment() {
    enum class MediaPickerIcon {
        ANDROID_CHOOSE_PHOTO,
        ANDROID_CHOOSE_VIDEO,
        ANDROID_CHOOSE_PHOTO_OR_VIDEO,
        ANDROID_CHOOSE_FILE,
        WP_STORIES_CAPTURE;
    }

    /*
     * parent activity must implement this listener
     */
    interface MediaPickerListener {
        fun onMediaChosen(uriList: List<Uri>)
        fun onIconClicked(icon: MediaPickerIcon, allowMultipleSelection: Boolean)
    }

    private var listener: MediaPickerListener? = null

    @Inject lateinit var tenorFeatureConfig: TenorFeatureConfig
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
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
        var selectedUris: List<UriWrapper>? = null
        var lastTappedIcon: MediaPickerIcon? = null
        if (savedInstanceState != null) {
            val savedLastTappedIconName = savedInstanceState.getString(KEY_LAST_TAPPED_ICON)
            lastTappedIcon = savedLastTappedIconName?.let { MediaPickerIcon.valueOf(it) }
            if (savedInstanceState.containsKey(KEY_SELECTED_POSITIONS)) {
                selectedUris = savedInstanceState.getStringArrayList(KEY_SELECTED_POSITIONS)
                        ?.map { UriWrapper(Uri.parse(it)) }
            }
        }
        recycler.setEmptyView(actionable_empty_view)
        recycler.setHasFixedSize(true)

        val layoutManager = GridLayoutManager(
                activity,
                NUM_COLUMNS
        )

        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        recycler.layoutManager = layoutManager

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
            event.getContentIfNotHandled()?.let { selectedUris ->
                listener?.onMediaChosen(selectedUris.map { it.uri })
            }
        })

        viewModel.onIconClicked.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { (icon, allowMultipleSelection) ->
                listener?.onIconClicked(icon, allowMultipleSelection)
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

        viewModel.start(selectedUris, mediaPickerSetup, lastTappedIcon, site)
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
            } else if (uiState.searchUiModel is SearchUiModel.Collapsed && searchMenuItem.isActionViewExpanded) {
                searchMenuItem.collapseActionView()
            }

            searchMenuItem.isVisible = uiState.searchUiModel.isVisible
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
        if (uiModel is PhotoListUiModel.Data) {
            if (recycler.adapter == null) {
                recycler.adapter = MediaPickerAdapter(
                        imageManager
                )
            }
            val adapter = recycler.adapter as MediaPickerAdapter
            val recyclerViewState = recycler.layoutManager?.onSaveInstanceState()
            adapter.loadData(uiModel.items)
            recycler.layoutManager?.onRestoreInstanceState(recyclerViewState)
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
                KEY_LAST_TAPPED_ICON,
                viewModel.lastTappedIcon?.name
        )
        val selectedIds = viewModel.selectedUris.value
        if (selectedIds != null && selectedIds.isNotEmpty()) {
            outState.putStringArrayList(KEY_SELECTED_POSITIONS, ArrayList(selectedIds.map { it.uri.toString() }))
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

    /*
     * similar to the above but only repopulates if changes are detected
     */
    fun refresh() {
        if (!isAdded) {
            AppLog.w(
                    POSTS,
                    "Photo picker > can't refresh when not added"
            )
            return
        }
        viewModel.refreshData(false)
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
        val permissions = arrayOf(permission.WRITE_EXTERNAL_STORAGE)
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
        private const val KEY_SELECTED_POSITIONS = "selected_positions"
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
