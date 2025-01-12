package denisnumb.video_saver.model

data class UserSettings(
    var repoData: RepoData?=null,
    var token: String?=null,
    var videoQuality: Int=720,
    var requestCount: Int=10,
    var maxVideoCount: Int=100,
    var mergeLocalDataWithCloudData: Boolean = true
)
