package denisnumb.video_saver.model.user_data_objects

data class RawUserData(
    var channels: Map<String, String>,
    var videoFolders: Map<String, Map<String, String>>
)
