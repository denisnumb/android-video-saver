package denisnumb.video_saver.ui

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.model.responses.DownloadingStatus
import denisnumb.video_saver.model.responses.ResponseStatus
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.ui.bottomsheet.SaveVideoFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.copyUrlToClipboard
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.deleteDownloadedVideo
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.getDownloadPath
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.getDownloadedVideoFile
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.handleResponseError
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.openInVideoPlayer
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.openUrl
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showDialog
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone

abstract class BaseVideosFragment : Fragment(), ItemMenuFragment.ActionChoiceEvent {
    protected val maxVideoCount get() = viewModel.userSettings.maxVideoCount
    protected val maxQuality get() = viewModel.userSettings.videoQuality

    protected lateinit var viewModel: SharedViewModel
    protected lateinit var videosAdapter: VideosAdapter

    protected abstract val currentVideoList: List<FullVideoData>
    protected abstract fun openVideoActionsMenu(video: FullVideoData)
    protected fun getVideoIndex(videoHash: String): Int = currentVideoList.indexOfFirst { it.hash == videoHash }

    override fun actionChoice(actionType: ItemMenuFragment.ActionType, key: String, position: Int) {
        val video = currentVideoList[position]

        when (actionType){
            ItemMenuFragment.ActionType.Remove -> removeVideoAction(video)

            ItemMenuFragment.ActionType.Edit -> openVideoEditDialogAction(video)

            ItemMenuFragment.ActionType.Copy -> requireContext().copyUrlToClipboard(video.url)

            ItemMenuFragment.ActionType.OpenInBrowser -> openUrl(video.url)

            ItemMenuFragment.ActionType.RemoveFromCache -> {
                video.sourceUrl = null
                videosAdapter.updateItem(position)
                requireContext().showText(resources.getString(R.string.removed_from_cache))
            }

            ItemMenuFragment.ActionType.OpenChannel -> openVideoChannelAction(video)

            ItemMenuFragment.ActionType.SaveVideo -> {
                val args = Bundle()
                args.putString(SaveVideoFragment.ARG_TITLE, video.title)
                args.putString(SaveVideoFragment.ARG_URL, video.url)
                args.putInt(SaveVideoFragment.ARG_INDEX, position)

                val addOrEditItemFragment = SaveVideoFragment()
                addOrEditItemFragment.arguments = args
                addOrEditItemFragment.show(childFragmentManager, "SaveVideoDialog")
            }

            ItemMenuFragment.ActionType.DownloadVideo -> downloadVideo(video)

            ItemMenuFragment.ActionType.DeleteDownloadedVideo -> {
                requireActivity().deleteDownloadedVideo(viewModel, video)
                videosAdapter.updateItem(position)
            }

            ItemMenuFragment.ActionType.CancelDownloading -> viewModel.stopDownloadingVideo(video)
        }
    }

    protected abstract fun openVideoChannelAction(video: FullVideoData)
    protected abstract fun openVideoEditDialogAction(video: FullVideoData)
    protected abstract fun removeVideoAction(video: FullVideoData)

    protected fun observeDownloadingProgresses() {
        viewModel.downloadingVideos.observe(viewLifecycleOwner) { progressMap ->
            for ((videoHash, progress) in progressMap) {
                val index = getVideoIndex(videoHash)
                if (index == -1)
                    continue

                val video = currentVideoList[index]

                if (!video.isDownloading && progress.status == DownloadingStatus.DOWNLOADING)
                    videosAdapter.setVideoIsDownloading(index, true)

                when (progress.status) {
                    DownloadingStatus.DOWNLOADING -> videosAdapter.updateVideoProgress(index, progress.progress)
                    DownloadingStatus.READY -> viewModel.downloadedHashes.add(videoHash)
                    DownloadingStatus.ERROR -> requireContext().showDialog(resources.getString(R.string.error_downloading_video, video.title, progress.errorMessage))
                    DownloadingStatus.CANCELED -> requireContext().showText(resources.getString(R.string.loading_canceled))
                    DownloadingStatus.HANDLED -> {}
                }

                if (progress.status != DownloadingStatus.DOWNLOADING)
                    progress.status = DownloadingStatus.HANDLED

                if (video.isDownloading && progress.status != DownloadingStatus.DOWNLOADING) {
                    videosAdapter.setVideoIsDownloading(index, false)
                }
            }
        }
    }

    protected fun observeLoadedVideos(){
        viewModel.loadingVideosLiveData.observe(viewLifecycleOwner) { responsesMap ->
            val handled = mutableListOf<String>()
            for ((videoHash, response) in responsesMap){
                val index = getVideoIndex(videoHash)
                if (index == -1)
                    continue

                val video = response.videoData!!
                if (video.isLoading){
                    video.isLoading = false
                    videosAdapter.notifyItemChanged(index)
                }
                else
                    videosAdapter.setVideoIsSourceUrlLoading(index, false)

                if (response.response.status != ResponseStatus.OK)
                    requireContext().handleResponseError(response.response)

                handled.add(videoHash)
            }

            handled.forEach { videoHash ->
                viewModel.loadingVideos.remove(videoHash)
            }
            viewModel.updateLoadingVideos()
        }
    }

    protected fun loadVideo(video: FullVideoData, loadOnlySourceUrl: Boolean=false){
        val index = getVideoIndex(video.hash)
        if (index != -1){
            if (loadOnlySourceUrl)
                videosAdapter.setVideoIsSourceUrlLoading(index, true)
            else
                videosAdapter.setVideoIsLoading(index, true)
            viewModel.loadFullVideoData(video, maxQuality)
        }
    }

    private fun downloadVideo(video: FullVideoData){
        requireActivity().getDownloadPath(viewModel)?.let {
            val index = getVideoIndex(video.hash)
            if (index != -1){
                if (!video.isLoaded && !video.isLoadingData)
                    loadVideo(video)
                videosAdapter.setVideoIsDownloading(index, true)
                viewModel.downloadVideo(requireActivity(), video, maxQuality)
            }
        }
    }

    protected fun createVideosAdapter(): VideosAdapter {
        val adapter = VideosAdapter(viewModel)
        adapter.setOnItemClickListener(object : VideosAdapter.VideoClickHandler {
            override fun onClickEvent(video: FullVideoData) {
                if (video.isDownloaded(viewModel.downloadedHashes))
                    requireContext().openInVideoPlayer(requireActivity().getDownloadedVideoFile(viewModel, video)?.uri)
                else if (video.isLoading)
                    requireContext().showText(resources.getString(R.string.video_is_loading))
                else if (video.isSourceUrlLoading)
                    requireContext().showText(resources.getString(R.string.getting_src))
                else if (video.sourceUrl != null)
                    requireContext().openInVideoPlayer(Uri.parse(video.sourceUrl!!))
                else if (video.isLoaded)
                    loadVideo(video, true)

                if (!video.isLoaded && !video.isLoadingData)
                    loadVideo(video)
            }

            override fun onLongClickEvent(video: FullVideoData): Boolean {
                vibratePhone(50)
                openVideoActionsMenu(video)
                return true
            }
        })
        return adapter
    }
}