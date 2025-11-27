package denisnumb.video_saver.ui.shared_loading

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.model.responses.FullVideoDataResponse
import denisnumb.video_saver.utils.ExtensionFunctions
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.handleResponseError
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.openInVideoPlayer
import denisnumb.video_saver.utils.YdlRequestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharedLoadingViewModel : ViewModel() {

    private var loadingJob: Job? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadVideoByUrl(url: String, context: Context, sharedViewModel: SharedViewModel) {
        loadingJob?.cancel()

        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    val videoData = VideoData(url, ExtensionFunctions.MD5(url))
                    val quality = sharedViewModel.userSettings.videoQuality
                    val response: FullVideoDataResponse = YdlRequestUtils.getFullVideoData(videoData, quality)

                    response.videoData?.sourceUrl?.let { sourceUrl ->
                        if (isLoading.value)
                            context.openInVideoPlayer(Uri.parse(sourceUrl))
                    } ?: context.handleResponseError(response.response)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelLoading() {
        loadingJob?.cancel()
        _isLoading.value = false
    }
}
