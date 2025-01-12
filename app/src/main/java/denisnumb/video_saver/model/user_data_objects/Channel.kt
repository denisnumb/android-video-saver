package denisnumb.video_saver.model.user_data_objects

data class Channel(
    override val title: String,
    override var url: String
) : IUserDataObject
