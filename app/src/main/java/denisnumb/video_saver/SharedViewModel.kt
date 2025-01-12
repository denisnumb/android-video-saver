package denisnumb.video_saver

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import denisnumb.video_saver.model.*
import denisnumb.video_saver.model.user_data_objects.Channel
import denisnumb.video_saver.model.user_data_objects.UserData
import denisnumb.video_saver.model.user_data_objects.RawUserData
import denisnumb.video_saver.model.user_data_objects.VideoFolder
import denisnumb.video_saver.model.responses.DownloadingProgress
import denisnumb.video_saver.model.responses.DownloadingStatus
import denisnumb.video_saver.model.responses.FullVideoDataResponse
import denisnumb.video_saver.model.responses.Response
import denisnumb.video_saver.model.responses.ResponseStatus
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.createFileInDirectory
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.getDownloadPath
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isInternetAvailable
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.requestUriPermissions
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveDownloadPath
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserDataLocal
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.UserDataUtils
import denisnumb.video_saver.utils.YdlRequestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SharedViewModel : ViewModel() {
    private lateinit var selectFolderLauncher: ActivityResultLauncher<Uri?>
    private var isLocalStorage: Boolean = false
    var userSettings = UserSettings()
    var githubFileData: GithubFile? = null
    var userData: UserData = UserData(mutableMapOf(), mutableMapOf())

    val channelsList = MutableLiveData<List<Channel>>()
    val videoFoldersList = MutableLiveData<List<VideoFolder>>()
    val currentVideoFolder = MutableLiveData<VideoFolder>()
    val downloadsDirLiveData = MutableLiveData<Uri?>()

    var channelsCache = HashMap<String, LinkedHashSet<String>>() // { channelUrl: hashes }
    var searchCache = HashMap<String, LinkedHashSet<String>>() // { queryUrl: hashes }
    var videoCache = HashMap<String, FullVideoData>()
    var searchQueries = HashMap<String, String>() // { query: url }
    var downloadedHashes = mutableSetOf<String>()

    private fun updateLiveData(){
        channelsList.postValue(userData.channels.values.toList())
        videoFoldersList.postValue(userData.videoFolders.values.toList())
        updateCurrentVideoFolder()
    }

    private fun updateCurrentVideoFolder(){
        currentVideoFolder.value?.let { currentFolder ->
            setCurrentVideoFolder(userData.videoFolders[currentFolder.name])
        }
    }
    fun setCurrentVideoFolder(videoFolder: VideoFolder?){
        videoFolder?.let {
            currentVideoFolder.postValue(it)
        }
    }

    fun loadData(context: Context, loadLocalData: Boolean=false){
        if (userSettings.repoData == null || loadLocalData || !isInternetAvailable(context)){
            isLocalStorage = true
            (context as Activity).getPreferences(MODE_PRIVATE).getString(Constants.USER_DATA, null)?.let { userData ->
                this.userData = UserDataUtils.convertRawUserDataToUserData(Gson().fromJson(userData, RawUserData::class.java))
            }
            if (loadLocalData)
                context.showText(context.resources.getString(R.string.local_data_loaded))

            return updateLiveData()
        }

        val response = UserDataUtils.getUserData(context, userSettings.repoData!!, userSettings.token.orEmpty())

        if (response.isSuccessful){
            githubFileData = response.githubFile

            if (userSettings.mergeLocalDataWithCloudData && isLocalStorage){
                userData = UserDataUtils.mergeUserDataObjects(userData, response.userDataData)
                isLocalStorage = false
                context.saveUserData(this, "Sync")
                context.showText(context.getString(R.string.data_is_synchronized))
            }
            else {
                userData = response.userDataData
                context.saveUserDataLocal(this)
            }

            updateLiveData()
        }
        else {
            githubFileData = null
            loadData(context, true)
        }
    }

    fun registerLauncher(activity: AppCompatActivity) {
        selectFolderLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                if (activity.requestUriPermissions(it)){
                    activity.saveDownloadPath(it.toString())
                    updateDownloadDirLiveData(it)
                }
            }
        }
    }
    fun updateDownloadDirLiveData(newUri: Uri?){
        if (downloadsDirLiveData.value != newUri)
            downloadsDirLiveData.postValue(newUri)
    }
    fun launchFolderPicker() {
        selectFolderLauncher.launch(null)
    }

    val downloadingProgresses = mutableMapOf<String, DownloadingProgress>()
    private val _downloadingVideos = MutableLiveData<Map<String, DownloadingProgress>>()
    val downloadingVideos: LiveData<Map<String, DownloadingProgress>> get() = _downloadingVideos
    private fun updateDownloadingProgress(videoHash: String, progress: DownloadingProgress){
        downloadingProgresses[videoHash] = progress
        updateDownloadingVideos()
    }
    fun updateDownloadingVideos(){
        _downloadingVideos.postValue(downloadingProgresses.toMap())
    }
    fun stopDownloadingVideo(video: FullVideoData){
        YoutubeDL.getInstance().destroyProcessById(video.hash)
    }

    fun downloadVideo(activity: Activity, video: FullVideoData, quality: Int){
        val hash = video.hash
        val downloadsDir = activity.getDownloadPath(this, false) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            updateDownloadingProgress(hash, DownloadingProgress())
            YdlRequestUtils.downloadVideo(activity, video, quality,
                progressHandler = { progress ->
                    val currentProgress = downloadingProgresses[hash]!!.progress
                    if (progress - currentProgress > 3)
                        updateDownloadingProgress(hash, DownloadingProgress(progress = progress))
                },

                onError = { exception ->
                    if (exception.message.isNullOrEmpty())
                        updateDownloadingProgress(hash, DownloadingProgress(DownloadingStatus.CANCELED))
                    else
                        updateDownloadingProgress(hash, DownloadingProgress(DownloadingStatus.ERROR, errorMessage = exception.message.toString()))
                },

                onSuccess = { tempFile ->
                    activity.createFileInDirectory(downloadsDir, tempFile.name)?.let { videoFile ->
                        activity.contentResolver.openOutputStream(videoFile)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                            tempFile.delete()
                        }
                        updateDownloadingProgress(hash, DownloadingProgress(DownloadingStatus.READY, progress = 100))
                    } ?: run {
                        updateDownloadingProgress(hash, DownloadingProgress(
                            DownloadingStatus.ERROR,
                            errorMessage = activity.resources.getString(R.string.file_create_error)
                        ))
                    }
                }
            )
        }
    }

    val loadingVideos = mutableMapOf<String, FullVideoDataResponse>()
    private val _loadingVideos = MutableLiveData<Map<String, FullVideoDataResponse>>()
    val loadingVideosLiveData: LiveData<Map<String, FullVideoDataResponse>> get() = _loadingVideos
    fun updateLoadingVideos(){
        _loadingVideos.postValue(loadingVideos.toMap())
    }

    fun loadFullVideoData(video: FullVideoData, quality: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedData = try{
                withTimeout(60000){
                    YdlRequestUtils.getFullVideoData(VideoData(video.url, video.title), quality)
                }
            } catch (e: Exception){
                YoutubeDL.getInstance().destroyProcessById(video.url)
                FullVideoDataResponse(response = Response(status = ResponseStatus.ERROR, message = e.message))
            }
            val loadedVideo = loadedData.videoData ?: video
            video.isLoading = video.isLoading
            video.isSourceUrlLoading = video.isSourceUrlLoading
            video.title = loadedVideo.title
            video.url = loadedVideo.url
            video.sourceUrl = loadedVideo.sourceUrl
            video.duration = loadedVideo.duration
            video.thumbnailUrl = loadedVideo.thumbnailUrl
            video.id = loadedVideo.id
            video.channelUrl = loadedVideo.channelUrl
            video.channelName = loadedVideo.channelName

            loadedData.videoData = video
            loadingVideos[video.hash] = loadedData
            updateLoadingVideos()
        }
    }
}