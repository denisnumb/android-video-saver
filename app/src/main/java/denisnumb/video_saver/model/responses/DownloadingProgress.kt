package denisnumb.video_saver.model.responses

data class DownloadingProgress(
    var status: DownloadingStatus = DownloadingStatus.DOWNLOADING,
    var progress: Int = 0,
    var errorMessage: String? = null
)

enum class DownloadingStatus {
    READY, DOWNLOADING, ERROR, CANCELED, HANDLED
}
