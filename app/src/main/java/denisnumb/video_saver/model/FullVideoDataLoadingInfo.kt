package denisnumb.video_saver.model

data class FullVideoDataLoadingInfo(
    var isLoading: Boolean = false,               // загружается полная информация о видео
    var isSourceUrlLoading: Boolean = false,     // загружается прямая ссылка
    var isDownloading: Boolean = false,         // видео скачивается
)
