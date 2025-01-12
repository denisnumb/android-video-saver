package denisnumb.video_saver.ui.videos

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import denisnumb.video_saver.databinding.ItemVideoFolderBinding
import denisnumb.video_saver.model.user_data_objects.VideoFolder

class VideoFoldersAdapter : RecyclerView.Adapter<VideoFoldersAdapter.ViewHolder>() {
    private lateinit var clickHandler: VideoFolderClickListener

    interface VideoFolderClickListener {
        fun onClickEvent(folder: VideoFolder)
        fun onLongClickEvent(folder: VideoFolder): Boolean
    }

    fun setOnItemClickListener(listener: VideoFolderClickListener){
        clickHandler = listener
    }

    private val differ = AsyncListDiffer(this, EventDiffUtilCallback())
    var data: List<VideoFolder>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    inner class ViewHolder(val binding: ItemVideoFolderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemVideoFolderBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = data[position]
        holder.binding.tvVideoName.text = folder.name
        holder.binding.tvElementCount.text = folder.videos.size.toString()

        holder.binding.cvVideo.setOnClickListener {
            clickHandler.onClickEvent(folder)
        }
        holder.binding.cvVideo.setOnLongClickListener {
            clickHandler.onLongClickEvent(folder)
        }

    }

    override fun getItemCount(): Int {
        return data.size
    }

    private class EventDiffUtilCallback : DiffUtil.ItemCallback<VideoFolder>() {
        override fun areItemsTheSame(oldItem: VideoFolder, newItem: VideoFolder): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: VideoFolder, newItem: VideoFolder): Boolean {
            return oldItem == newItem
        }
    }

}