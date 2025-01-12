package denisnumb.video_saver.ui.view_channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentSearchBinding
import denisnumb.video_saver.model.user_data_objects.Channel
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.ui.BaseWebVideosFragment
import denisnumb.video_saver.ui.bottomsheet.AddOrEditItemFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isDuplicate
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.prepareToSaveData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveQueryCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveVideoCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.updateData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone

class ViewChannelFragment : BaseWebVideosFragment<ViewChannelViewModel>(), AddOrEditItemFragment.AddNewItemClickListener {
    override var _binding: FragmentSearchBinding? = null
    override val queryCache: HashMap<String, LinkedHashSet<String>> get() = viewModel.channelsCache
    private val isCurrentChannelInUserData: Boolean get()
        = videosViewModel.currentRequestUrl in viewModel.channelsList.value.orEmpty().map { it.url }
    private var channelName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[(SharedViewModel::class.java)]
        videosViewModel = ViewModelProvider(requireActivity())[ViewChannelViewModel::class.java]
        _binding = FragmentSearchBinding.inflate(inflater, container, false)

        val currentUrl = requireArguments().getString(ARG_REQUEST_URL, null)
        channelName =  requireArguments().getString(ARG_TITLE, null)
        channelName?.let { title ->
            (activity as AppCompatActivity).supportActionBar?.title = title
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.view_channel_toolbar, menu)
                removeFromCacheMenuItem = menu.getItem(0)
                removeFromCacheMenuItem.isVisible = 
                    cachedList.isNotEmpty() && !videosViewModel.isLoading
                menu.getItem(1).isVisible = !isCurrentChannelInUserData
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        return true
                    }
                    R.id.remove_channel_from_cache -> {
                        menuItem.isVisible = false
                        videosViewModel.clearCacheForUrl(videosViewModel.currentRequestUrl!!, queryCache)
                        setCardViewLoadVisibility(true)
                        setFragmentIsEmptyVisibility(true)
                        requireContext().showText(resources.getString(R.string.channel_removed_from_cache))
                        true
                    }
                    R.id.save_channel -> {
                        if (requireContext().prepareToSaveData(viewModel)){
                            val args = Bundle()
                            args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(R.string.new_channel))
                            args.putString(AddOrEditItemFragment.ARG_SET_NAME, channelName)
                            args.putString(AddOrEditItemFragment.ARG_SET_URL, videosViewModel.currentRequestUrl!!)
                            val addOrEditItemFragment = AddOrEditItemFragment()
                            addOrEditItemFragment.arguments = args
                            addOrEditItemFragment.show(childFragmentManager, "AddNewChannelDialog")
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        videosAdapter = createVideosAdapter()
        binding.rvVideos.adapter = videosAdapter

        videosViewModel.setCurrentRequestUrl(currentUrl)

        observeVideoList()
        observeCurrentUrl()
        observeIsLoading()
        observeIsLastLoadingEmpty()
        observeDownloadingProgresses()
        observeLoadedVideos()

        setProgressText(resources.getString(R.string.progress,0, maxVideoCount))

        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            if (!videosViewModel.isLoading)
                loadPage()
        }

        binding.buttonSearch.setOnClickListener {
            if (videosViewModel.isLoading)
                videosViewModel.cancelLoadingJob()
            else
                loadPage()
        }

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        if (isCurrentChannelInUserData){
            videosViewModel.saveCurrentVideoListToCache(queryCache, viewModel.videoCache)
            saveQueryCache(queryCache, denisnumb.video_saver.Constants.CHANNELS_CACHE)
            saveVideoCache(viewModel.videoCache)
        }
    }

    override fun openVideoChannelAction(video: FullVideoData) {}
    override fun openVideoEditDialogAction(video: FullVideoData) {}
    override fun removeVideoAction(video: FullVideoData) {}

    override fun loadPage(){
        videosViewModel.loadPage(chunkSize, maxVideoCount, maxQuality)
    }

    override fun openVideoActionsMenu(video: FullVideoData){
        vibratePhone(50)
        val args = Bundle()
        args.putInt(ItemMenuFragment.ARG_INDEX, currentVideoList.indexOf(video))
        args.putString(ItemMenuFragment.ARG_TITLE, video.title)
        args.putString(ItemMenuFragment.ARG_KEY, video.url)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_REMOVE_BUTTON, false)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_OPEN_IN_BROWSER_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_REMOVE_FROM_CACHE_BUTTON, !video.sourceUrl.isNullOrEmpty())
        args.putBoolean(ItemMenuFragment.ARG_SHOW_SAVE_VIDEO_BUTTON, !video.isLoading)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DOWNLOAD_VIDEO_BUTTON, !video.isLoadingAny && !video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DELETE_DOWNLOADED_VIDEO_BUTTON, video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_CANCEL_DOWNLOADING_BUTTON, video.isDownloading)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }

    override fun addOrEditClickEvent(
        fragment: AddOrEditItemFragment,
        title: String,
        url: String,
        mode: AddOrEditItemFragment.AddOrEdit
    ) {
        if ((viewModel.userData.channels.values.any { it.isDuplicate(title, url) }) && mode == AddOrEditItemFragment.AddOrEdit.Add)
            return requireContext().showText(resources.getString(R.string.channel_already_exists))

        fragment.dismiss()
        viewModel.userData.channels[title] = Channel(title, url)
        val commitSymbol = if (mode == AddOrEditItemFragment.AddOrEdit.Add) "+" else "E"
        requireContext().saveUserData(viewModel, "[$commitSymbol] Каналы: $title")
        requireContext().updateData(viewModel, 1000)
        requireContext().showText(resources.getString(R.string.saved))
    }
}