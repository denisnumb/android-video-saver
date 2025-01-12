package denisnumb.video_saver.ui.search

import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.ui.BaseWebVideosViewModel
import denisnumb.video_saver.utils.YdlRequestUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koitharu.pausingcoroutinedispatcher.launchPausing

class SearchViewModel : BaseWebVideosViewModel() {
    var currentQuery: String? = null
    fun setQuery(value: String){
        currentQuery = value
    }

    override fun loadPage(chunkSize: Int, maxVideoCount: Int, maxQuality: Int) {
        if (currentRequestUrl == null)
            return

        val loadingJob = viewModelScope.launchPausing(Dispatchers.IO){
            setIsLoading()
            val beforeLoadingCount = currentVideoList.value?.size ?: 0
            var pageVideos: List<VideoData>? = null
            val requestUrl = currentRequestUrl!!

            try{
                val pageVideosResponse = async { YdlRequestUtils.getPageVideos(requestUrl, maxVideoCount) }.await()
                _lastLoadingResponse = pageVideosResponse.response
                pageVideos = pageVideosResponse.pageVideos

                pageVideos?.let {
                    val allPageVideos = it.take(maxVideoCount)

                    if (currentVideoList.value.isNullOrEmpty()){
                        launch { handleAllPageVideos(allPageVideos, chunkSize, maxQuality) }.join()
                    }
                    else {
                        val oldVideos = async { handleNextVideos(allPageVideos) }.await()
                        launch { loadPageVideos(oldVideos, chunkSize, allPageVideos.size-oldVideos.size, maxQuality) }.join()
                    }
                }
            } catch (e: CancellationException){
                YoutubeDL.getInstance().destroyProcessById(requestUrl)
                pageVideos?.forEach { videoData ->
                    YoutubeDL.getInstance().destroyProcessById(videoData.url)
                }
            } finally {
                val afterLoadingCount = currentVideoList.value?.size ?: 0
                setIsLastLoadingEmpty(!isLoadingJobCanceled && beforeLoadingCount == afterLoadingCount)
                setIsLoading(false)
            }
        }
        setLoadingJob(loadingJob)
    }
}