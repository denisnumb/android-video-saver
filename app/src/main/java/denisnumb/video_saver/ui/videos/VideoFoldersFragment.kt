package denisnumb.video_saver.ui.videos

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentListBinding
import denisnumb.video_saver.model.user_data_objects.VideoFolder
import denisnumb.video_saver.ui.bottomsheet.AddOrEditItemFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.prepareToSaveData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.updateData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone

class VideoFoldersFragment : Fragment(),
    AddOrEditItemFragment.AddNewItemClickListener,
    ItemMenuFragment.ActionChoiceEvent {

    private lateinit var viewModel: SharedViewModel
    private lateinit var binding: FragmentListBinding
    private lateinit var videoFoldersAdapter: VideoFoldersAdapter
    private lateinit var downloadedVideosFolder: VideoFolder

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        binding = FragmentListBinding.inflate(inflater, container, false)

        videoFoldersAdapter = createVideoFoldersAdapter()
        binding.rvItems.adapter = videoFoldersAdapter

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.add_button_toolbar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.add -> {
                        addFolder()
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.refreshLayout.setOnRefreshListener {
            updateData()
        }

        downloadedVideosFolder = VideoFolder(
            resources.getString(R.string.title_downloaded_videos),
            DownloadedVideosFragment.findDownloadedVideos(viewModel).toMutableList(),
            isDownloadsFolder = true
        )

        viewModel.videoFoldersList.value?.let { videoFolders ->
            updateAdapterData(videoFolders)

            if (videoFolders.isEmpty() && viewModel.userSettings.repoData != null)
                updateData()
        }

        viewModel.videoFoldersList.observe(viewLifecycleOwner) { videoFolders ->
            updateAdapterData(videoFolders)
            binding.tvEmpty.isVisible = videoFoldersAdapter.data.isEmpty()
        }

        return binding.root
    }

    override fun addOrEditClickEvent(fragment: AddOrEditItemFragment, title: String, url: String, mode: AddOrEditItemFragment.AddOrEdit) {
        if (viewModel.userData.videoFolders.containsKey(title)){
            requireContext().showText(resources.getString(R.string.folder_already_exists))
            return
        }
        fragment.dismiss()

        viewModel.userData.videoFolders[title] = VideoFolder(title, mutableListOf())
        requireContext().saveUserData(viewModel, "[+] Видео-папка: $title")
        updateData(1000)
    }

    override fun actionChoice(actionType: ItemMenuFragment.ActionType, key: String, position: Int) {
        when (actionType){
            ItemMenuFragment.ActionType.Remove -> {
                if (!requireContext().prepareToSaveData(viewModel))
                    return

                viewModel.userData.videoFolders.remove(key)
                requireContext().saveUserData(viewModel, "[-] Видео-папка: $key")
                updateData(1000)
            }
            else -> { }
        }
    }

    private fun updateData(timeoutMilliseconds: Long=0){
        requireContext().updateData(viewModel, timeoutMilliseconds){
            binding.refreshLayout.isRefreshing = false
            binding.tvEmpty.isVisible = videoFoldersAdapter.data.isEmpty()
        }
    }

    private fun createVideoFoldersAdapter(): VideoFoldersAdapter {
        val adapter = VideoFoldersAdapter()
        adapter.setOnItemClickListener(object : VideoFoldersAdapter.VideoFolderClickListener{
            override fun onClickEvent(folder: VideoFolder) {
                if (folder.isDownloadsFolder)
                    findNavController().navigate(R.id.action_navigation_videosFolder_to_downloaded_videos)
                else {
                    viewModel.setCurrentVideoFolder(folder)
                    findNavController().navigate(R.id.action_navigation_videosFolder_to_videos)
                }

            }

            override fun onLongClickEvent(folder: VideoFolder): Boolean {
                vibratePhone(50)
                if (!folder.isDownloadsFolder)
                    openActionsMenu(folder)
                return true
            }

        })

        return adapter
    }

    private fun addFolder(){
        if (!requireContext().prepareToSaveData(viewModel))
            return

        val args = Bundle()
        args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(R.string.new_video_folder))
        args.putBoolean(AddOrEditItemFragment.ARG_REQUIRE_URL, false)

        val addOrEditItemFragment = AddOrEditItemFragment()
        addOrEditItemFragment.arguments = args
        addOrEditItemFragment.show(childFragmentManager, "AddNewFolderDialog")
    }

    private fun openActionsMenu(folder: VideoFolder){
        val args = Bundle()
        args.putString(ItemMenuFragment.ARG_TITLE, folder.name)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_EDIT_URL_BUTTON, false)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, false)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }

    private fun updateAdapterData(folders: List<VideoFolder>){
        if (downloadedVideosFolder.videos.isNotEmpty())
        {
            val foldersList = folders.toMutableList()
            foldersList.add(0, downloadedVideosFolder)
            videoFoldersAdapter.data = foldersList
        }
        else videoFoldersAdapter.data = folders
    }
}