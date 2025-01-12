package denisnumb.video_saver.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import denisnumb.video_saver.databinding.ItemQueryBinding

class SearchHistoryAdapter : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {
    private lateinit var clickHandler: QueryClickListener

    interface QueryClickListener {
        fun onClickEvent(query: String)
        fun onLongClickEvent(query: String, position: Int): Boolean
    }

    fun setOnItemClickListener(listener: QueryClickListener){
        clickHandler = listener
    }

    private val differ = AsyncListDiffer(this, EventDiffUtilCallback())
    var data: List<String>
        get() = differ.currentList
        set(value) = differ.submitList(value)

    inner class ViewHolder(val binding: ItemQueryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemQueryBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val query = data[position]

        holder.binding.tvQuery.text = query

        holder.binding.cvQuery.setOnClickListener {
            clickHandler.onClickEvent(query)
        }
        holder.binding.cvQuery.setOnLongClickListener {
            clickHandler.onLongClickEvent(query, position)
        }

    }

    override fun getItemCount(): Int {
        return data.size
    }

    private class EventDiffUtilCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }

}