package denisnumb.video_saver.model

import denisnumb.video_saver.model.user_data_objects.IUserDataObject
import denisnumb.video_saver.utils.ExtensionFunctions


data class FullVideoData(
    override var title: String,
    override var url: String,
    var sourceUrl: String? = null,
    var duration: String? = null,
    var thumbnailUrl: String? = null,
    var id: String? = null,
    var channelUrl: String? = null,
    var channelName: String? = null
) : IUserDataObject {
    @Transient
    private lateinit var _loadingInfo: FullVideoDataLoadingInfo
    val hash = ExtensionFunctions.MD5(url)

    var loadingInfo: FullVideoDataLoadingInfo
        get() {
            if (!this::_loadingInfo.isInitialized)
                _loadingInfo = FullVideoDataLoadingInfo()
            return _loadingInfo
        }
        set(value) { _loadingInfo = value }
    var isSourceUrlLoading: Boolean
        get() = loadingInfo.isSourceUrlLoading
        set(value) { loadingInfo.isSourceUrlLoading = value }
    var isLoading: Boolean
        get() = loadingInfo.isLoading
        set(value) { loadingInfo.isLoading = value }
    var isDownloading: Boolean
        get() = loadingInfo.isDownloading
        set(value) { loadingInfo.isDownloading = value }
    val isLoaded: Boolean get() = id != null
    val isLoadingData: Boolean get() = loadingInfo.isLoading || loadingInfo.isSourceUrlLoading
    val isLoadingAny: Boolean get() = isLoadingData || isDownloading
    fun isDownloaded(downloadedHashes: Set<String>): Boolean = downloadedHashes.contains(hash)
}
