package denisnumb.video_saver.ui.view_channel

import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.ui.BaseWebVideosViewModel
import denisnumb.video_saver.utils.YdlRequestUtils
import kotlinx.coroutines.Dispatchers
import org.koitharu.pausingcoroutinedispatcher.launchPausing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ViewChannelViewModel : BaseWebVideosViewModel() {
    override fun loadPage(chunkSize: Int, maxVideoCount: Int, maxQuality: Int) {
        val loadingJob = viewModelScope.launchPausing(Dispatchers.IO){
            setIsLoading()
            val beforeLoadingCount = currentVideoList.value?.size ?: 0
            var pageVideos: List<VideoData>? = null
            val requestUrl = currentRequestUrl!!

            try{
                val pageVideosResponse = async { YdlRequestUtils.getPageVideos(requestUrl, maxVideoCount) }.await()
                _lastLoadingResponse = pageVideosResponse.response
                pageVideos = pageVideosResponse.pageVideos

                pageVideos?.let { allPageVideos ->
                    if (currentVideoList.value.isNullOrEmpty()){
                        launch { handleAllPageVideos(allPageVideos, chunkSize, maxQuality) }.join()
                    }
                    else {
                        val newVideos = async { handleNewVideos(allPageVideos) }.await()
                        val oldVideos = async { handleNextVideos(allPageVideos) }.await()
                        launch { loadPageVideos(newVideos, chunkSize, 0, maxQuality) }.join()
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