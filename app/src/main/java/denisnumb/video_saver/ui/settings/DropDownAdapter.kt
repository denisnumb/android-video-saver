package denisnumb.video_saver.ui.settings

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class DropDownAdapter<T>(context: Context, resource: Int, val items: List<T>) :
    ArrayAdapter<T>(context, resource, items) {
    private val noOpFilter: Filter

    override fun getFilter(): Filter {
        return noOpFilter as Filter
    }

    init {
        noOpFilter = object : Filter() {
            private val noOpResult: FilterResults = FilterResults()
            override fun performFiltering(charSequence: CharSequence?): FilterResults {
                return noOpResult
            }

            override fun publishResults(
                charSequence: CharSequence?,
                filterResults: FilterResults?
            ) {
            }
        }
    }
}