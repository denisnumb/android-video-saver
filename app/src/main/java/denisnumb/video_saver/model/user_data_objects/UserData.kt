package denisnumb.video_saver.model.user_data_objects

data class UserData(
    var channels: MutableMap<String, Channel>,
    var videoFolders: MutableMap<String, VideoFolder>
)
