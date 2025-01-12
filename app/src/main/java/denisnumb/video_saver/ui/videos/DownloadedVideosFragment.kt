package denisnumb.video_saver.ui.videos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentListBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.ui.BaseVideosFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.deleteDownloadedVideo

class DownloadedVideosFragment : BaseVideosFragment() {

    companion object {
        fun findDownloadedVideos(viewModel: SharedViewModel): List<FullVideoData>
            = viewModel.videoCache.values.filter { it.hash in viewModel.downloadedHashes }
    }

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var downloadedVideos: List<FullVideoData> = emptyList()
    override val currentVideoList: List<FullVideoData> get() = downloadedVideos

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        _binding = FragmentListBinding.inflate(inflater, container, false)

        videosAdapter = createVideosAdapter()
        binding.rvItems.adapter = videosAdapter

        updateDownloadedVideos()
        observeLoadedVideos()

        binding.refreshLayout.setOnRefreshListener {
            updateDownloadedVideos()
            binding.refreshLayout.isRefreshing = false
        }

        return binding.root
    }

    override fun openVideoActionsMenu(video: FullVideoData) {
        val args = Bundle()
        args.putInt(ItemMenuFragment.ARG_INDEX, currentVideoList.indexOf(video))
        args.putString(ItemMenuFragment.ARG_TITLE, video.title)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, true)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }

    override fun openVideoChannelAction(video: FullVideoData) {}

    override fun openVideoEditDialogAction(video: FullVideoData) {}

    override fun removeVideoAction(video: FullVideoData) {
        requireActivity().deleteDownloadedVideo(viewModel, video)
        updateDownloadedVideos()
    }

    private fun updateDownloadedVideos() {
        downloadedVideos = findDownloadedVideos(viewModel)
        videosAdapter.data = downloadedVideos
        binding.tvEmpty.isVisible = downloadedVideos.isEmpty()
    }
}