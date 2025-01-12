package denisnumb.video_saver.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentListBinding
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.ui.search.SearchFragment.Companion.ARG_QUERY
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveSearchQueries
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone

class SearchHistoryFragment : Fragment(), ItemMenuFragment.ActionChoiceEvent {

    private lateinit var viewModel: SharedViewModel
    private lateinit var binding: FragmentListBinding
    private lateinit var adapter: SearchHistoryAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        binding = FragmentListBinding.inflate(inflater, container, false)

        adapter = createAdapter()
        binding.rvItems.adapter = adapter
        setAdapterData(viewModel.searchQueries.keys.toList())

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.search_history_toolbar, menu)
                val searchField = menu.getItem(0).actionView as SearchView
                searchField.queryHint = resources.getString(R.string.enter_query_or_url)
                setSearchViewOnQueryTextListener(searchField)
                searchField.isIconified = false
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        applyQuery(null)
                        return true
                    }
                    R.id.clear_search_history -> {
                        viewModel.searchCache = HashMap()
                        viewModel.searchQueries = HashMap()
                        saveSearchQueries(viewModel)
                        setAdapterData(emptyList())
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)



        return binding.root
    }

    override fun actionChoice(actionType: ItemMenuFragment.ActionType, key: String, position: Int) {
        viewModel.searchCache.remove(viewModel.searchQueries[key])
        viewModel.searchQueries.remove(key)
        setAdapterData(viewModel.searchCache.keys.toList())
    }

    private fun setAdapterData(queries: List<String>){
        adapter.data = queries
        binding.tvEmpty.isVisible = queries.isEmpty()
    }

    private fun setSearchViewOnQueryTextListener(searchField: SearchView){
        searchField.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                applyQuery(query)
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty())
                    setAdapterData(viewModel.searchQueries.keys.toList())
                else
                    setAdapterData(viewModel.searchQueries.keys.filter { it.contains(newText) })
                return false
            }
        })
    }

    private fun applyQuery(query: String?) {
        val queryWithoutExtraSpaces = if (query.isNullOrEmpty())
            query else Regex("\\s+").replace(query, " ").trim()

        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set(ARG_QUERY, queryWithoutExtraSpaces)
        navController.popBackStack()
    }

    private fun createAdapter(): SearchHistoryAdapter {
        val adapter = SearchHistoryAdapter()
        adapter.setOnItemClickListener(object : SearchHistoryAdapter.QueryClickListener{
            override fun onClickEvent(query: String) {
                applyQuery(query)
            }
            override fun onLongClickEvent(query: String, position: Int): Boolean {
                vibratePhone(50)
                openActionsMenu(query, position)
                return true
            }
        })

        return adapter
    }

    private fun openActionsMenu(query: String, position: Int){
        val args = Bundle()
        args.putString(ItemMenuFragment.ARG_TITLE, query)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_EDIT_URL_BUTTON, false)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, false)
        args.putInt(ItemMenuFragment.ARG_INDEX, position)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }
}