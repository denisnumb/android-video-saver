package denisnumb.video_saver.model.responses

import denisnumb.video_saver.model.FullVideoData

data class FullVideoDataResponse(
    val response: Response = Response(ResponseStatus.OK),
    var videoData: FullVideoData? = null
)