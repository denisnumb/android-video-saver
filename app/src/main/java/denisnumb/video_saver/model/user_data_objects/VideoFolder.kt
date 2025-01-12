package denisnumb.video_saver.model.user_data_objects

import denisnumb.video_saver.model.FullVideoData

data class VideoFolder(
    var name: String,
    val videos: MutableList<FullVideoData>,
    val isDownloadsFolder: Boolean=false
)
