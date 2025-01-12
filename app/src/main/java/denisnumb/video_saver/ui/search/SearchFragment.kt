package denisnumb.video_saver.ui.search

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.Constants.Companion.SEARCH_CACHE
import denisnumb.video_saver.databinding.FragmentSearchBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.ui.BaseWebVideosFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveQueryCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveSearchQueries
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveVideoCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone
import denisnumb.video_saver.utils.YdlRequestUtils.Companion.BASE_SEARCH_URL


class SearchFragment : BaseWebVideosFragment<SearchViewModel>() {

    override var _binding: FragmentSearchBinding? = null
    override val queryCache: HashMap<String, LinkedHashSet<String>> get() = viewModel.searchCache
    private var searchSubmitted: Boolean = false

    companion object{
        const val ARG_QUERY = "query"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[(SharedViewModel::class.java)]
        videosViewModel = ViewModelProvider(requireActivity())[SearchViewModel::class.java]
        _binding = FragmentSearchBinding.inflate(inflater, container, false)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.search_toolbar, menu)
                removeFromCacheMenuItem = menu.getItem(1)
                removeFromCacheMenuItem.isVisible =
                    cachedList.isNotEmpty() && !videosViewModel.isLoading
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.search_field -> {
                        findNavController().navigate(R.id.action_navigation_search_to_search_history)
                        true
                    }
                    R.id.remove_query_from_cache -> {
                        videosViewModel.currentRequestUrl?.let { currentUrl ->
                            menuItem.isVisible = false
                            videosViewModel.clearCacheForUrl(currentUrl, queryCache)
                            viewModel.searchQueries.remove(videosViewModel.currentQuery)
                            setCardViewLoadVisibility(true)
                            setFragmentIsEmptyVisibility(true)
                            requireContext().showText(resources.getString(R.string.query_removed_from_cache))
                        }
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        videosAdapter = createVideosAdapter()
        binding.rvVideos.adapter = videosAdapter

        videosViewModel.currentQuery?.let { currentQuery ->
            (activity as AppCompatActivity).supportActionBar?.title = currentQuery
        }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>(ARG_QUERY)
            ?.observe(viewLifecycleOwner) {
                searchSubmitted = it != null
                it?.let{ query ->
                    videosViewModel.setQuery(query)
                    val requestUrl = buildRequestUrl(query)
                    videosViewModel.setCurrentRequestUrl(requestUrl)
                    (activity as AppCompatActivity).supportActionBar?.title = query
                }
            }

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
        videosViewModel.saveCurrentVideoListToCache(queryCache, viewModel.videoCache)
        saveQueryCache(queryCache, SEARCH_CACHE)
        saveVideoCache(viewModel.videoCache)
        saveSearchQueries(viewModel)
    }

    override fun onResume() {
        super.onResume()
        val inputMethodManager = requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun observeCurrentUrl() {
        videosViewModel.currentRequestUrlLiveData.observe(viewLifecycleOwner) { url ->
            if (url != videosViewModel.previousRequestUrl && searchSubmitted){
                if (videosViewModel.isLoading)
                    videosViewModel.cancelLoadingJob()
                loadPage()

                videosViewModel.setIsLastLoadingEmpty(false)
                videosViewModel.setCurrentVideoList(cachedList)
                setCardViewLoadVisibility(cachedList.isEmpty())
            }
        }
    }
    override fun openVideoChannelAction(video: FullVideoData) {
        val args = Bundle()
        args.putString(ARG_REQUEST_URL, video.channelUrl)
        args.putString(ARG_TITLE, video.channelName)
        findNavController().navigate(R.id.action_navigation_search_to_view_channel, args)
    }
    override fun openVideoEditDialogAction(video: FullVideoData) {}

    override fun removeVideoAction(video: FullVideoData) {
        videosViewModel.removeVideoFromList(video)
        viewModel.stopDownloadingVideo(video)
    }

    override fun loadPage(){
        if (videosViewModel.currentRequestUrl.isNullOrEmpty())
            requireContext().showText(resources.getString(R.string.enter_query_or_url))
        else if (loadedVideoCount == maxVideoCount)
            requireContext().showText(resources.getString(R.string.max_video_count_already_loaded))
        else {
            viewModel.searchQueries[videosViewModel.currentQuery!!] = videosViewModel.currentRequestUrl!!
            videosViewModel.loadPage(chunkSize, maxVideoCount, maxQuality)
        }
    }

    override fun openVideoActionsMenu(video: FullVideoData){
        vibratePhone(50)
        val args = Bundle()
        args.putInt(ItemMenuFragment.ARG_INDEX, currentVideoList.indexOf(video))
        args.putString(ItemMenuFragment.ARG_TITLE, video.title)
        args.putString(ItemMenuFragment.ARG_KEY, video.url)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_OPEN_IN_BROWSER_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_REMOVE_BUTTON, !videosViewModel.isLoading)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_OPEN_CHANNEL_BUTTON, !video.isLoading)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_SAVE_VIDEO_BUTTON, !video.isLoading)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DOWNLOAD_VIDEO_BUTTON, !video.isLoadingAny && !video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_DELETE_DOWNLOADED_VIDEO_BUTTON, video.isDownloaded(viewModel.downloadedHashes))
        args.putBoolean(ItemMenuFragment.ARG_SHOW_CANCEL_DOWNLOADING_BUTTON, video.isDownloading)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }

    private fun buildRequestUrl(query: String): String{
        val queryWithOnlyLetters = query.filter { it.isWhitespace() || it.isLetter() }
        val queryWithoutExtraSpaces = Regex("\\s+").replace(queryWithOnlyLetters, " ").trim()
        val request = queryWithoutExtraSpaces.replace(" ", "+")

        return BASE_SEARCH_URL + request
    }
}