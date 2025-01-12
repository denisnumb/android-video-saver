package denisnumb.video_saver.ui.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import denisnumb.video_saver.databinding.ItemChannelBinding
import denisnumb.video_saver.model.user_data_objects.Channel

class ChannelsAdapter : RecyclerView.Adapter<ChannelsAdapter.ViewHolder>() {
    private lateinit var clickHandler: ChannelClickListener

    interface ChannelClickListener {
        fun onClickEvent(channel: Channel)
        fun onLongClickEvent(channel: Channel): Boolean
    }

    fun setOnItemClickListener(listener: ChannelClickListener){
        clickHandler = listener
    }

    private val differ = AsyncListDiffer(this, EventDiffUtilCallback())
    var data: List<Channel>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    inner class ViewHolder(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChannelBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = data[position]
        holder.binding.channel = channel

        holder.binding.cvChannel.setOnClickListener {
            clickHandler.onClickEvent(channel)
        }
        holder.binding.cvChannel.setOnLongClickListener {
            clickHandler.onLongClickEvent(channel)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    private class EventDiffUtilCallback : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean {
            return oldItem == newItem
        }
    }
}