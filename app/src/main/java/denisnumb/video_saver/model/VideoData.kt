package denisnumb.video_saver.model

import com.google.gson.annotations.SerializedName

data class VideoData(
    @SerializedName("url")
    val url: String,
    @SerializedName("title")
    val title: String?
)
