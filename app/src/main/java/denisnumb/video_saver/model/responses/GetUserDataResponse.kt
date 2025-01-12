package denisnumb.video_saver.model.responses

import denisnumb.video_saver.model.GithubFile
import denisnumb.video_saver.model.user_data_objects.UserData

data class GetUserDataResponse(
    val userDataData: UserData,
    val githubFile: GithubFile?,
    val isSuccessful: Boolean=false
)
