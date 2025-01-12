package denisnumb.video_saver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.ItemVideoBinding
import denisnumb.video_saver.model.FullVideoData


class VideosAdapter(
    private val viewModel: SharedViewModel
) : RecyclerView.Adapter<VideosAdapter.ViewHolder>() {
    private lateinit var clickHandler: VideoClickHandler

    interface VideoClickHandler {
        fun onClickEvent(video: FullVideoData)
        fun onLongClickEvent(video: FullVideoData): Boolean
    }

    fun setOnItemClickListener(listener: VideoClickHandler){
        clickHandler = listener
    }

    private val differ = AsyncListDiffer(this, EventDiffUtilCallback())
    var data: List<FullVideoData>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    inner class ViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: FullVideoData){
            binding.video = video
            updateVisibility(video)

            binding.cvVideo.setOnClickListener {
                clickHandler.onClickEvent(video)
            }
            binding.cvVideo.setOnLongClickListener {
                clickHandler.onLongClickEvent(video)
            }
        }

        fun updateDownloadingProgress(progress: Int){
            binding.pbLoading.progress = progress
        }

        fun updateVisibility(video: FullVideoData) = with(binding) {
            val videoIsDownloaded = !video.isDownloading && video.isDownloaded(viewModel.downloadedHashes)
            pbLoading.isIndeterminate = !video.isDownloading
            pbLoading.isVisible = video.isLoadingAny && !videoIsDownloaded
            ivDownloaded.isVisible = videoIsDownloaded
            ivSrcUrlAvailible.isVisible = !video.sourceUrl.isNullOrEmpty() && !videoIsDownloaded && !pbLoading.isVisible

            layoutStatus.visibility = if (pbLoading.isVisible
                || ivSrcUrlAvailible.isVisible
                || ivDownloaded.isVisible) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVideoBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val video = data[position]

        if (payloads.isEmpty())
            holder.bind(video)
        else{
            for (payload in payloads){
                when (payload){
                    is DownloadingProgressPayload -> holder.updateDownloadingProgress(payload.progress)
                    is EmptyPayload -> holder.updateVisibility(video)
                }
            }
        }

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    private class EventDiffUtilCallback : DiffUtil.ItemCallback<FullVideoData>() {
        override fun areItemsTheSame(oldItem: FullVideoData, newItem: FullVideoData): Boolean {
            return oldItem.hash == newItem.hash
        }

        override fun areContentsTheSame(oldItem: FullVideoData, newItem: FullVideoData): Boolean {
            return oldItem == newItem
        }
    }

    fun updateItem(position: Int){
        notifyItemChanged(position, EmptyPayload())
    }
    fun updateVideoProgress(position: Int, progress: Int){
        notifyItemChanged(position, DownloadingProgressPayload(progress))
    }

    fun setVideoIsDownloading(position: Int, isDownloading: Boolean){
        data[position].isDownloading = isDownloading
        notifyItemChanged(position, EmptyPayload())
    }

    fun setVideoIsLoading(position: Int, isLoading: Boolean){
        data[position].isLoading = isLoading
        notifyItemChanged(position, EmptyPayload())
    }

    fun setVideoIsSourceUrlLoading(position: Int, isSourceUrlLoading: Boolean){
        data[position].isSourceUrlLoading = isSourceUrlLoading
        notifyItemChanged(position, EmptyPayload())
    }

    data class DownloadingProgressPayload(val progress: Int)
    class EmptyPayload
}