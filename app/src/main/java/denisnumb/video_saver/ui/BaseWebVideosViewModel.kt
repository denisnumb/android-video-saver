package denisnumb.video_saver.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.model.responses.Response
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.utils.YdlRequestUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koitharu.pausingcoroutinedispatcher.PausingJob

abstract class BaseWebVideosViewModel : ViewModel() {
    private val _currentRequestUrl = MutableLiveData<String>(null)
    val currentRequestUrl: String? get() = _currentRequestUrl.value
    val currentRequestUrlLiveData: LiveData<String> get() = _currentRequestUrl
    var previousRequestUrl: String? = null

    fun setCurrentRequestUrl(value: String){
        previousRequestUrl = _currentRequestUrl.value
        _currentRequestUrl.value = value
    }

    protected var _lastLoadingResponse: Response? = null
    val lastLoadingResponse: Response? get() = _lastLoadingResponse

    private val _isLastLoadingEmpty = MutableLiveData(false)
    val isLastLoadingEmpty: Boolean get() = _isLastLoadingEmpty.value!!
    val isLastLoadingEmptyLiveData: LiveData<Boolean> get() = _isLastLoadingEmpty
    fun setIsLastLoadingEmpty(value: Boolean=true){
        _isLastLoadingEmpty.postValue(value)
    }

    private val _isLoading = MutableLiveData(false)
    val isLoadingLiveData: LiveData<Boolean> get() = _isLoading
    val isLoading: Boolean get() = _isLoading.value ?: false
    protected fun setIsLoading(value: Boolean=true) {
        _isLoading.postValue(value)
    }

    private var _loadingJob: PausingJob? = null
    protected val isLoadingJobCanceled: Boolean get() = _loadingJob?.isCancelled ?: false

    protected fun setLoadingJob(job: PausingJob){
        _loadingJob = job
    }
    fun cancelLoadingJob(){
        _loadingJob?.cancel()
    }
    fun pauseLoadingJob(){
        _loadingJob?.let { job ->
            if (!job.isPaused)
                job.pause()
        }
    }
    fun resumeLoadingJob(){
        _loadingJob?.let { job ->
            if (job.isPaused)
                job.resume()
        }
    }

    private val _currentVideoList = MutableLiveData<List<FullVideoData>>()
    val currentVideoList: LiveData<List<FullVideoData>> get() = _currentVideoList

    private fun postCurrentVideoList(newList: List<FullVideoData>){
        _currentVideoList.postValue(newList)
    }
    fun setCurrentVideoList(newList: List<FullVideoData>){
        _currentVideoList.value = newList
    }

    fun saveCurrentVideoListToCache(
        queryCache: HashMap<String, LinkedHashSet<String>>,
        videoCache: HashMap<String, FullVideoData>
    ) {
        _currentVideoList.value?.let { currentVideoList ->
            _currentRequestUrl.value?.let { requestUrl ->
                val loadedFiltered = currentVideoList.filter { !it.isLoading }
                queryCache[requestUrl] = loadedFiltered.map { it.hash }.toMutableSet() as LinkedHashSet<String>
                loadedFiltered.forEach { videoCache[it.hash] = it }
            }
        }
    }
    fun clearCacheForUrl(url: String, cache: HashMap<String, LinkedHashSet<String>>){
        cache.remove(url)
        postCurrentVideoList(emptyList())
    }

    private fun insertNewVideosToList(newVideos: List<FullVideoData>){
        val currentList = _currentVideoList.value!!.toMutableList()
        newVideos.forEachIndexed { index, videoData ->
            currentList.add(index, videoData)
        }
        postCurrentVideoList(currentList)
    }
    private fun addOldVideosToList(oldVideos: List<FullVideoData>){
        val currentList = _currentVideoList.value!!.toMutableList()
        oldVideos.forEach{ videoData ->
            currentList.add(videoData)
        }
        postCurrentVideoList(currentList)
    }
    private fun setLoadedVideoChunk(chunk: List<Pair<Int, FullVideoData>>){
        val currentList = _currentVideoList.value.orEmpty().toMutableList()
        chunk.forEach {
            if (currentList.size > it.first)
                currentList[it.first] = it.second
        }
        postCurrentVideoList(currentList)
    }
    fun removeUnloadedVideos(){
        val currentList = _currentVideoList.value.orEmpty().toMutableList()
        val filteredList = currentList.filter { !it.isLoading }

        if (filteredList.size != currentList.size)
            setCurrentVideoList(filteredList)
    }
    fun removeVideoFromList(video: FullVideoData){
        val currentList = _currentVideoList.value.orEmpty().toMutableList()

        if (video in currentList){
            currentList.remove(video)
            postCurrentVideoList(currentList)
        }
    }

    private fun getNewVideos(actualVideos: List<VideoData>): List<VideoData> {
        val currentUrls = _currentVideoList.value!!.map { it.url }
        return actualVideos.takeWhile { it.url !in currentUrls }
    }
    private fun getNextVideos(actualVideos: List<VideoData>): List<VideoData> {
        val currentUrls = _currentVideoList.value!!.map { it.url }
        return actualVideos.takeLastWhile { it.url !in currentUrls }
    }

    protected suspend fun handleAllPageVideos(pageVideos: List<VideoData>, chunkSize: Int, maxQuality: Int) = coroutineScope{
        withContext(Dispatchers.Main){
            postCurrentVideoList(pageVideos.map {
                FullVideoData(it.title ?: ". . .", it.url).apply { isLoading = true }
            })
        }
        loadPageVideos(pageVideos, chunkSize, 0, maxQuality)
    }
    protected suspend fun handleNewVideos(pageVideos: List<VideoData>): List<VideoData> {
        val newVideos = getNewVideos(pageVideos)
        withContext(Dispatchers.Main){
            insertNewVideosToList(newVideos.map {
                FullVideoData(it.title ?: ". . .", it.url).apply { isLoading = true }
            })
        }
        return newVideos
    }
    protected suspend fun handleNextVideos(pageVideos: List<VideoData>): List<VideoData> {
        val oldVideos = getNextVideos(pageVideos)
        withContext(Dispatchers.Main){
            addOldVideosToList(oldVideos.map {
                FullVideoData(it.title ?: ". . .", it.url).apply { isLoading = true }
            })
        }
        return oldVideos
    }

    abstract fun loadPage(chunkSize: Int, maxVideoCount: Int, maxQuality: Int)

    protected suspend fun loadPageVideos(
        pageVideos: List<VideoData>,
        chunkSize: Int,
        startIndex: Int=0,
        maxQuality: Int
    ) = coroutineScope{
        var totalIndex = startIndex

        pageVideos.chunked(chunkSize).forEach { chunk ->
            val fullVideoDataDeferredList = mutableListOf<Deferred<Pair<Int, FullVideoData?>>>()

            chunk.forEach { videoData ->
                fullVideoDataDeferredList.add(
                    async(Dispatchers.IO){
                        val videoIndex = totalIndex++
                        val fullVideoData = YdlRequestUtils.getFullVideoData(videoData, maxQuality).videoData
                        videoIndex to fullVideoData
                    }
                )
            }

            fullVideoDataDeferredList.awaitAll()
                .filter { it.second != null }
                .sortedBy { it.first }
                .map { Pair(it.first, it.second!!) }
                .let {
                    setLoadedVideoChunk(it)
                }
        }
    }
}