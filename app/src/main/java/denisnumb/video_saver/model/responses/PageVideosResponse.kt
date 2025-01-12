package denisnumb.video_saver.model.responses

import denisnumb.video_saver.model.VideoData

data class PageVideosResponse(
    val response: Response = Response(ResponseStatus.OK),
    val pageVideos: List<VideoData>? = null
)
