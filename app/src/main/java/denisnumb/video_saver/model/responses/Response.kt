package denisnumb.video_saver.model.responses

data class Response(
    val status: ResponseStatus,
    val exitCode: Int? = null,
    val message: String? = null
)
