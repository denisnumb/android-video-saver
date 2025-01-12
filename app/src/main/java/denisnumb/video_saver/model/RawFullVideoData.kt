package denisnumb.video_saver.model

import com.google.gson.annotations.SerializedName

data class RawFullVideoData(
    @SerializedName("url")
    val url: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("duration")
    val duration: Int,
    @SerializedName("thumbnail")
    val thumbnailUrl: String,
    @SerializedName("id")
    val id: String,
    @SerializedName("uploader")
    val uploader: String,
    @SerializedName("uploader_id")
    val uploaderId: String,
    @SerializedName("original_url")
    val originalUrl: String
)
