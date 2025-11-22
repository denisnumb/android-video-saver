package denisnumb.video_saver.ui.videos

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentListBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.ui.BaseVideosFragment
import denisnumb.video_saver.ui.BaseWebVideosFragment
import denisnumb.video_saver.ui.bottomsheet.AddOrEditItemFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isDuplicate
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.prepareToSaveData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveVideoCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.updateData

class VideosFragment : BaseVideosFragment(), AddOrEditItemFragment.AddNewItemClickListener  {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    
    override val currentVideoList: List<FullVideoData> get() {
        return viewModel.currentVideoFolder.value?.videos?.map { video ->
            viewModel.videoCache.values.find { it.hash == video.hash } ?: video
        } ?: emptyList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        _binding = FragmentListBinding.inflate(inflater, container, false)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.add_button_toolbar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        return true
                    }
                    R.id.add -> {
                        addVideo()
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel.currentVideoFolder.value?.name?.let { videoFolderName ->
            (activity as AppCompatActivity).supportActionBar?.title = videoFolderName
        }

        videosAdapter = createVideosAdapter()
        binding.rvItems.adapter = videosAdapter

        observeVideoFolder()
        observeLoadedVideos()
        observeDownloadingProgresses()

        viewModel.downloadsDirLiveData.observe(viewLifecycleOwner){
            videosAdapter.data.forEachIndexed{ index, _ ->
                videosAdapter.updateItem(index)
            }
        }

        binding.refreshLayout.setOnRefreshListener {
            updateData()
        }

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        currentVideoList.filter { !it.isLoading && (it.isLoaded || it.isDownloaded(viewModel.downloadedHashes)) }.forEach { video ->
            viewModel.videoCache[video.hash] = video
        }
        saveVideoCache(viewModel.videoCache)
    }

    override fun openVideoActionsMenu(video: FullVideoData) {
        val args = Bundle()
        args.putInt(ItemMenuFragment.ARG_INDEX, currentVideoList.indexOf(video))
        args.putString(ItemMenuFragment.ARG_TITLE, video.title)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_EDIT_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_OPEN_IN_BROWSER_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_REMOVE_FROM_CACHE_BUTTON, !video.sourceUrl.isNullOrEmpty())
        args.putBoolean(
            ItemMenuFragment.ARG_SHOW_OPEN_CHANNEL_BUTTON,
            !video.channelUrl.isNullOrEmpty() && !video.channelName.isNullOrEmpty()
        )
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DOWNLOAD_VIDEO_BUTTON, !video.isLoadingAny && !video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DELETE_DOWNLOADED_VIDEO_BUTTON, video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_CANCEL_DOWNLOADING_BUTTON, video.isDownloading)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }

    override fun addOrEditClickEvent(fragment: AddOrEditItemFragment, title: String, url: String, mode: AddOrEditItemFragment.AddOrEdit) {
        viewModel.currentVideoFolder.value?.videos?.let { videos ->
            if (videos.any { it.isDuplicate(title, url) } && mode == AddOrEditItemFragment.AddOrEdit.Add)
                return requireContext().showText(resources.getString(R.string.video_already_exists))
        }
        fragment.dismiss()

        if (mode == AddOrEditItemFragment.AddOrEdit.Add)
            viewModel.currentVideoFolder.value?.videos?.add(FullVideoData(title, url))
        else {
            viewModel.currentVideoFolder.value?.let { folder ->
                val updatedVideos = folder.videos.map { video ->
                    if (video.title == title) video.copy(url = url) else video
                }.toMutableList()

                val updatedFolder = folder.copy(name = folder.name, videos = updatedVideos)
                viewModel.setCurrentVideoFolder(updatedFolder)
                viewModel.userData.videoFolders[folder.name] = updatedFolder
            }
        }

        val commitSymbol = if (mode == AddOrEditItemFragment.AddOrEdit.Add) "+" else "E"
        requireContext().saveUserData(viewModel, "[$commitSymbol] Видео: $title")
        updateData(3000)

        if (mode == AddOrEditItemFragment.AddOrEdit.Edit)
            requireContext().showText(resources.getString(R.string.saved))
    }

    override fun openVideoChannelAction(video: FullVideoData) {
        val args = Bundle()
        args.putString(BaseWebVideosFragment.ARG_REQUEST_URL, video.channelUrl)
        args.putString(BaseWebVideosFragment.ARG_TITLE, video.channelName)
        findNavController().navigate(R.id.action_navigation_videos_to_view_channel, args)
    }

    override fun openVideoEditDialogAction(video: FullVideoData) {
        if (!requireContext().prepareToSaveData(viewModel))
            return

        val args = Bundle()
        args.putString(AddOrEditItemFragment.ARG_TITLE, video.title)
        args.putBoolean(AddOrEditItemFragment.ARG_REQUIRE_NAME, false)
        args.putBoolean(AddOrEditItemFragment.ARG_IS_EDIT, true)
        args.putString(AddOrEditItemFragment.ARG_SET_NAME, video.title)
        args.putString(AddOrEditItemFragment.ARG_SET_URL, video.url)

        val addOrEditItemFragment = AddOrEditItemFragment()
        addOrEditItemFragment.arguments = args
        addOrEditItemFragment.show(childFragmentManager, "EditUrlDialog")
    }

    override fun removeVideoAction(video: FullVideoData) {
        if (!requireContext().prepareToSaveData(viewModel))
            return

        val index = getVideoIndex(video.hash)
        viewModel.stopDownloadingVideo(video)

        viewModel.currentVideoFolder.value!!.videos.removeAt(index)
        requireContext().saveUserData(viewModel, "[-] Видео: ${video.title}")
        updateData(1000)
    }


    private fun observeVideoFolder(){
        viewModel.currentVideoFolder.observe(viewLifecycleOwner){ folder ->
            videosAdapter.data = currentVideoList
            setFragmentIsEmptyVisibility(folder.videos.isEmpty())
        }
    }

    private fun setFragmentIsEmptyVisibility(value: Boolean) {
        binding.tvEmpty.isVisible = value
    }

    private fun updateData(timeoutMilliseconds: Long=0){
        requireContext().updateData(viewModel, timeoutMilliseconds) {
            binding.refreshLayout.isRefreshing = false
            setFragmentIsEmptyVisibility(viewModel.currentVideoFolder.value?.videos?.isEmpty() ?: true)
        }
    }

    private fun addVideo(){
        if (!requireContext().prepareToSaveData(viewModel))
            return

        val args = Bundle()
        args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(R.string.new_video))
        val addOrEditItemFragment = AddOrEditItemFragment()
        addOrEditItemFragment.arguments = args
        addOrEditItemFragment.show(childFragmentManager, "AddNewVideoDialog")
    }
}