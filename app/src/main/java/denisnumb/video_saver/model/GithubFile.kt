package denisnumb.video_saver.model

import com.google.gson.annotations.SerializedName

data class GithubFile(

    @SerializedName("name"         ) var name        : String,
    @SerializedName("path"         ) var path        : String,
    @SerializedName("sha"          ) var sha         : String,
    @SerializedName("size"         ) var size        : Int,
    @SerializedName("url"          ) var url         : String,
    @SerializedName("html_url"     ) var htmlUrl     : String,
    @SerializedName("git_url"      ) var gitUrl      : String,
    @SerializedName("download_url" ) var downloadUrl : String,
    @SerializedName("type"         ) var type        : String,
    @SerializedName("content"      ) var content     : String,
    @SerializedName("encoding"     ) var encoding    : String,
    @SerializedName("_links"       ) var Links       : Map<String, String>

)
