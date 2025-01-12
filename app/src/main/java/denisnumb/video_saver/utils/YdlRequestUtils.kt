package denisnumb.video_saver.utils

import android.content.Context
import com.google.gson.GsonBuilder
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.model.RawFullVideoData
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.model.responses.FullVideoDataResponse
import denisnumb.video_saver.model.responses.PageVideosResponse
import denisnumb.video_saver.model.responses.Response
import denisnumb.video_saver.model.responses.ResponseStatus
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File


class YdlRequestUtils {
    companion object {
        const val BASE_SEARCH_URL = "https://www.youtube.com/results?search_query="
        private const val BASE_YT_CHANNEL_URL = "https://www.youtube.com/%s/videos"
        private const val BASE_VK_GROUP_URL = "https://vkvideo.ru/@club%s/all"
        private const val BASE_VK_USER_URL = "https://vkvideo.ru/@id%s?section=uploaded"

        private fun formatSecondsToMMSS(seconds: Int): String {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60

            return "%02d:%02d".format(minutes, remainingSeconds)
        }

        private fun makeChannelUrl(videoRawData: RawFullVideoData): String? {
            val uploaderId = videoRawData.uploaderId

            return if(videoRawData.originalUrl.contains("youtube.com")
                || videoRawData.originalUrl.contains("youtu.be"))
                BASE_YT_CHANNEL_URL.format(uploaderId)
            else if (videoRawData.originalUrl.contains("vk.com")
                || videoRawData.originalUrl.contains("vkvideo.ru")){
                String.format(
                    if (uploaderId.startsWith("-")) BASE_VK_GROUP_URL else BASE_VK_USER_URL,
                    uploaderId.replace("-", "")
                )
            }
            else null
        }

        private fun handleSpecialSymbols(title: String): String{
            return title
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
        }

        private fun getResponseErrorFromMessage(errorMessage: String): ResponseStatus {
            return if (errorMessage.contains("404"))
                ResponseStatus.NOT_FOUND
            else if (errorMessage.contains("available from"))
                ResponseStatus.NOT_AVAILABLE
            else
                ResponseStatus.ERROR
        }

        fun isSrcUrlAlive(url: String?): Boolean {
            if (url.isNullOrBlank())
                return false

            return try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }

        fun getPageVideos(pageUrl: String, maxVideoCount: Int): PageVideosResponse {
            val request = YoutubeDLRequest(pageUrl)
            request.addOption("--no-warnings")
            request.addOption("--print-json")
            request.addOption("--skip-download")
            request.addOption("--flat-playlist")
            request.addOption("--playlist-end", maxVideoCount)

            val gson = GsonBuilder().create()
            val response: PageVideosResponse

            try {
                val rawData = YoutubeDL.getInstance().execute(request, pageUrl).out.split("\n")
                response = PageVideosResponse(
                    pageVideos = rawData.filter { it.isNotBlank() }.map { videoData ->
                        gson.fromJson(videoData, VideoData::class.java)
                    }.filter { entry -> !listOf("channel", "playlist").any { entry.url.contains(it) } }
                )
            } catch (ex: Exception){
                val message = ex.message.toString()
                return PageVideosResponse(Response(getResponseErrorFromMessage(message), message = message))
            }

            return response
        }

        fun getFullVideoData(videoData: VideoData, maxQuality: Int): FullVideoDataResponse {
            val request = YoutubeDLRequest(videoData.url)
            request.addOption("-f", "best[height<=$maxQuality][protocol=https]")
            request.addOption("--print-json")
            request.addOption("--skip-download")
            request.addOption("--no-warnings")

            val processId = videoData.url
            val response: YoutubeDLResponse
            try {
                response = YoutubeDL.getInstance().execute(request, processId)
            } catch (ex: YoutubeDLException){
                val message = ex.message.toString()

                if (message == "Process ID already exists"){
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    return getFullVideoData(videoData, maxQuality)
                }

                return FullVideoDataResponse(Response(
                    getResponseErrorFromMessage(message),
                    message = message
                ))
            } catch (exc: Exception) {
                return FullVideoDataResponse(Response(
                    ResponseStatus.ERROR,
                    message = exc.message
                ))
            }

            val rawData = GsonBuilder().create().fromJson(response.out, RawFullVideoData::class.java)

            return FullVideoDataResponse(
                videoData = FullVideoData(
                    title = handleSpecialSymbols(videoData.title ?: rawData.title),
                    url = videoData.url,
                    sourceUrl = rawData.url,
                    duration = formatSecondsToMMSS(rawData.duration),
                    thumbnailUrl = rawData.thumbnailUrl,
                    id = rawData.id,
                    channelUrl = makeChannelUrl(rawData),
                    channelName = handleSpecialSymbols(rawData.uploader)
                )
            )
        }

        suspend fun downloadVideo(
            context: Context,
            video: FullVideoData,
            maxQuality: Int,
            progressHandler: (Int) -> Unit,
            onError: (Exception) -> Unit,
            onSuccess: (File) -> Unit
        ) = coroutineScope {
            val callback: Function3<Float, Long, String, Unit> = { progress: Float, _: Long?, _: String? ->
                progressHandler(progress.toInt())
            }

            val tempFile = File(context.cacheDir, video.hash)

            val request = YoutubeDLRequest(video.url)
            request.addOption("-f", "best[height<=$maxQuality][protocol=https]")
            request.addOption("--no-warnings")
            request.addOption("-o", tempFile.absolutePath)

            try {
                YoutubeDL.getInstance().execute(request, video.hash, callback)
                onSuccess(tempFile)
            } catch (e: Exception){
                File(context.cacheDir, video.hash + ".part").delete()
                onError(e)
            }
        }
    }
}