package denisnumb.video_saver.ui

import android.view.MenuItem
import androidx.core.view.isVisible
import denisnumb.video_saver.R
import denisnumb.video_saver.databinding.FragmentSearchBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.handleResponseError
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText

abstract class BaseWebVideosFragment<VM: BaseWebVideosViewModel> : BaseVideosFragment() {

    companion object {
        const val ARG_TITLE = "title"
        const val ARG_REQUEST_URL = "requestUrl"
    }

    protected abstract var _binding: FragmentSearchBinding?
    protected val binding get() = _binding!!
    protected val chunkSize get() = viewModel.userSettings.requestCount
    private val isCurrentVideoListIsEmpty: Boolean
        get() = videosViewModel.currentVideoList.value.isNullOrEmpty()
    protected val loadedVideoCount: Int
        get() = videosViewModel.currentVideoList.value?.filter { !it.isLoading }?.size ?: 0
    protected val cachedList: List<FullVideoData>
        get() {
            val cachedHashes = queryCache[videosViewModel.currentRequestUrl] ?: linkedSetOf()
            return cachedHashes.mapNotNull { hash -> viewModel.videoCache[hash] }
        }

    protected abstract val queryCache: HashMap<String, LinkedHashSet<String>>
    protected lateinit var videosViewModel: VM
    protected lateinit var removeFromCacheMenuItem: MenuItem

    override val currentVideoList: List<FullVideoData>
        get() = videosViewModel.currentVideoList.value.orEmpty()

    override fun onPause() {
        super.onPause()
        videosViewModel.pauseLoadingJob()

    }

    override fun onResume() {
        super.onResume()
        videosViewModel.resumeLoadingJob()
    }

    protected abstract fun loadPage()

    protected fun setCardViewLoadVisibility(value: Boolean){
        binding.cvLoad.isVisible = value
    }

    protected fun setProgressText(value: String) {
        binding.tvProgress.text = value
    }

    private fun setProgressBarVisibility(value: Boolean) {
        binding.pbLoading.isVisible = value
    }

    private fun setButtonLoadText(value: String) {
        binding.buttonSearch.text = value
    }

    protected fun setFragmentIsEmptyVisibility(value: Boolean) {
        binding.tvEmpty.isVisible = value
    }

    private fun setFragmentIsEmptyText(value: String) {
        binding.tvEmpty.text = value
    }

    protected open fun observeVideoList(){
        videosViewModel.currentVideoList.observe(viewLifecycleOwner) { videoList ->
            videosAdapter.data = videoList

            setProgressText(resources.getString(
                R.string.progress,
                loadedVideoCount,
                if (videoList.size == loadedVideoCount) maxVideoCount else videoList.size
            ))
        }
    }

    protected open fun observeCurrentUrl(){
        videosViewModel.currentRequestUrlLiveData.observe(viewLifecycleOwner) { url ->
            if (url != videosViewModel.previousRequestUrl){
                if (videosViewModel.isLoading)
                    videosViewModel.cancelLoadingJob()

                videosViewModel.setIsLastLoadingEmpty(false)
                videosViewModel.setCurrentVideoList(cachedList)
                setCardViewLoadVisibility(cachedList.isEmpty())
            }
        }
    }

    protected open fun observeIsLoading(){
        videosViewModel.isLoadingLiveData.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading)
                videosViewModel.removeUnloadedVideos()
            setCardViewLoadVisibility(isLoading || isCurrentVideoListIsEmpty)
            setFragmentIsEmptyVisibility(!isLoading && isCurrentVideoListIsEmpty)
            setProgressBarVisibility(isLoading)
            setButtonLoadText(resources.getString(
                if (isLoading) R.string.stop_loading else R.string.start_loading
            ))

            val isSameRequest = videosViewModel.currentRequestUrl == videosViewModel.previousRequestUrl
                    || videosViewModel.previousRequestUrl.isNullOrEmpty()
            val isLastLoadingFailed = videosViewModel.isLastLoadingEmpty && isSameRequest

            setFragmentIsEmptyText(resources.getString(
                if (isLastLoadingFailed) R.string.empty_fragment_loaded_videos else R.string.empty_fragment
            ))
            if (isLastLoadingFailed)
                videosViewModel.lastLoadingResponse?.let { response -> requireContext().handleResponseError(response) }

            if (this::removeFromCacheMenuItem.isInitialized){
                removeFromCacheMenuItem.isVisible = !isLoading
                        && cachedList.isNotEmpty()
            }
        }
    }

    protected open fun observeIsLastLoadingEmpty(){
        videosViewModel.isLastLoadingEmptyLiveData.observe(viewLifecycleOwner) { isLastLoadingEmpty ->
            if (!isCurrentVideoListIsEmpty && isLastLoadingEmpty)
                requireContext().showText(resources.getString(R.string.no_other_videos))
        }
    }
}