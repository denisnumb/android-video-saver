package denisnumb.video_saver.utils

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import denisnumb.video_saver.R
import denisnumb.video_saver.model.*
import denisnumb.video_saver.model.user_data_objects.Channel
import denisnumb.video_saver.model.user_data_objects.UserData
import denisnumb.video_saver.model.user_data_objects.RawUserData
import denisnumb.video_saver.model.user_data_objects.VideoFolder
import denisnumb.video_saver.model.responses.GetUserDataResponse
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showDialog
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showYesNoDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch

class UserDataUtils {
    companion object {
        fun mergeUserDataObjects(localUserData: UserData, cloudUserData: UserData): UserData{
            localUserData.channels.forEach { (name, channel) ->
                if (!cloudUserData.channels.containsKey(name)) {
                    cloudUserData.channels[name] = channel
                }
            }

            localUserData.videoFolders.forEach { (folderName, folder) ->
                if (!cloudUserData.videoFolders.containsKey(folderName)) {
                    cloudUserData.videoFolders[folderName] = folder
                } else {
                    val existingFolder = cloudUserData.videoFolders[folderName]
                    folder.videos.forEach { video ->
                        if (existingFolder?.videos?.none { it.title == video.title } == true) {
                            existingFolder.videos.add(video)
                        }
                    }
                }
            }

            return cloudUserData
        }

        fun convertUserDataToJson(userData: UserData): String {
            return GsonBuilder().setPrettyPrinting().create().toJson(RawUserData(
                channels = userData.channels.mapValues { (_, channel) -> channel.url },
                videoFolders = userData.videoFolders.mapValues { (_, folder) ->
                    folder.videos.associate { video ->
                        video.title to video.url
                    }
                }
            ))
        }

        fun convertRawUserDataToUserData(rawUserData: RawUserData): UserData {
            return UserData(
                channels = rawUserData.channels.mapValues { (name, url) ->
                    Channel(name, url)
                }.toMutableMap(),

                videoFolders = rawUserData.videoFolders.mapValues { (name, videos) ->
                    VideoFolder(name, videos.map { (name, url) ->
                        FullVideoData(name, url)
                    }.toMutableList())
                }.toMutableMap()
            )
        }

        fun saveUserData(
            context: Context,
            repoData: RepoData,
            fileData: GithubFile,
            commitMessage: String,
            userDataContent: UserData,
            token: String
        )
        {
            val content = convertUserDataToJson(userDataContent)
            val base64EncodedContent = String(Base64.encode(content.toByteArray(), Base64.DEFAULT))
            val jsonBody = JSONObject()
            jsonBody.put("message", commitMessage)
            jsonBody.put("content", base64EncodedContent)
            jsonBody.put("sha", fileData.sha)
            jsonBody.put("branch", repoData.branchName)

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.github.com/repos/${repoData.userName}/${repoData.repoName}/contents/${fileData.path}")
                .addHeader("Authorization", "token $token")
                .addHeader("Content-Type","application/json")
                .put(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).enqueue(object: Callback{
                override fun onFailure(call: Call, e: IOException) {
                    val message = if (e.message.isNullOrEmpty())
                        context.resources.getString(R.string.save_data_req_error, "")
                        else context.resources.getString(R.string.save_data_req_error, "Ошибка: ${e.message!!}")
                    context.showDialog(message)

                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful){
                        var errorMessage = ""
                        response.body?.let { body ->
                            val bodyString = body.string()
                            if (bodyString.isNotEmpty())
                                errorMessage = "Ошибка: $bodyString"
                        }
                        context.showDialog(context.resources.getString(R.string.save_data_error, response.code, errorMessage))
                    }
                }

            })
        }

        fun getUserData(
            context: Context,
            repoData: RepoData,
            token: String
        ): GetUserDataResponse {
            var result = GetUserDataResponse(
                userDataData = UserData(mutableMapOf(), mutableMapOf()),
                githubFile = null,
            )

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.github.com/repos/${repoData.userName}/${repoData.repoName}/contents/${repoData.filePath}")
                .addHeader("Authorization", "token $token")
                .build()

            val latch = CountDownLatch(1)

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val message = e.message ?: ""
                    context.showDialog(context.resources.getString(R.string.get_data_req_error, message))
                    latch.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val fileData = Gson().fromJson(response.body?.string(), GithubFile::class.java)
                            val userData = parseUserData(context, repoData, fileData, token)
                            result = GetUserDataResponse(userData, fileData, isSuccessful = true)
                        } catch (e: Exception) {
                            context.showDialog(context.resources.getString(R.string.error_reading_github_file))
                        }
                    } else {
                        val errorMessage = response.body?.string() ?: ""
                        context.showDialog(context.resources.getString(R.string.get_data_error, response.code, errorMessage))
                    }
                    latch.countDown()
                }
            })

            latch.await()
            return result
        }


        private fun parseUserData(
            context: Context,
            repoData: RepoData,
            fileData: GithubFile,
            token: String
        ): UserData {
            var raw: RawUserData? = null
            val emptyUserData = UserData(
                channels = mutableMapOf(),
                videoFolders = mutableMapOf()
            )

            try {
               raw = Gson().fromJson(String(Base64.decode(fileData.content, Base64.DEFAULT)), RawUserData::class.java)
            } catch (ex: Exception){
                CoroutineScope(Dispatchers.Main).launch {
                    context.showYesNoDialog(
                        context.resources.getString(R.string.error_parsing_github_file, fileData.name),
                        {saveUserData(context, repoData, fileData, "Reset File", emptyUserData, token)}
                    )
                }
            }

            raw?.let { rawUserData ->
                return convertRawUserDataToUserData(rawUserData)
            }

            return emptyUserData
        }
    }
}