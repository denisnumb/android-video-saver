package denisnumb.video_saver.ui.channels

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.databinding.FragmentListBinding
import denisnumb.video_saver.model.user_data_objects.Channel
import denisnumb.video_saver.ui.BaseWebVideosFragment.Companion.ARG_REQUEST_URL
import denisnumb.video_saver.ui.BaseWebVideosFragment.Companion.ARG_TITLE
import denisnumb.video_saver.ui.bottomsheet.AddOrEditItemFragment
import denisnumb.video_saver.ui.bottomsheet.ItemMenuFragment
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.copyUrlToClipboard
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isDuplicate
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.openUrl
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.prepareToSaveData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.updateData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone


class ChannelsFragment : Fragment(),
    AddOrEditItemFragment.AddNewItemClickListener,
    ItemMenuFragment.ActionChoiceEvent {

    private lateinit var viewModel: SharedViewModel
    private lateinit var binding: FragmentListBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[(SharedViewModel::class.java)]
        binding = FragmentListBinding.inflate(inflater, container, false)

        val channelsAdapter = createChannelsAdapter()
        binding.rvItems.adapter = channelsAdapter

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.add_button_toolbar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.add -> {
                        addChannel()
                        return true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.refreshLayout.setOnRefreshListener {
            updateData()
        }

        viewModel.channelsList.value?.let { channelsList ->
            channelsAdapter.data = channelsList

            if (channelsList.isEmpty() && viewModel.userSettings.repoData != null)
                updateData()
        }

        viewModel.channelsList.observe(viewLifecycleOwner) { channelsList ->
            channelsAdapter.data = channelsList
            binding.tvEmpty.isVisible = channelsList.isEmpty()
        }

        return binding.root
    }

    override fun addOrEditClickEvent(fragment: AddOrEditItemFragment, title: String, url: String, mode: AddOrEditItemFragment.AddOrEdit) {
        if ((viewModel.userData.channels.values.any { it.isDuplicate(title, url) }) && mode == AddOrEditItemFragment.AddOrEdit.Add)
            return requireContext().showText(resources.getString(R.string.channel_already_exists))

        fragment.dismiss()
        viewModel.userData.channels[title] = Channel(title, url)
        val commitSymbol = if (mode == AddOrEditItemFragment.AddOrEdit.Add) "+" else "E"
        requireContext().saveUserData(viewModel, "[$commitSymbol] Каналы: $title")
        updateData(1000)

        if (mode == AddOrEditItemFragment.AddOrEdit.Edit)
            requireContext().showText(resources.getString(R.string.saved))
    }

    override fun actionChoice(actionType: ItemMenuFragment.ActionType, key: String, position: Int) {
        when (actionType){
            ItemMenuFragment.ActionType.Remove -> {
                if (!requireContext().prepareToSaveData(viewModel))
                    return

                viewModel.userData.channels.remove(key)
                requireContext().saveUserData(viewModel, "[-] Каналы: $key")
                updateData(1000)
            }

            ItemMenuFragment.ActionType.Edit -> {
                if (!requireContext().prepareToSaveData(viewModel))
                    return

                val args = Bundle()
                args.putString(AddOrEditItemFragment.ARG_TITLE, key)
                args.putBoolean(AddOrEditItemFragment.ARG_REQUIRE_NAME, false)
                args.putBoolean(AddOrEditItemFragment.ARG_IS_EDIT, true)
                args.putString(AddOrEditItemFragment.ARG_SET_NAME, key)
                args.putString(AddOrEditItemFragment.ARG_SET_URL, viewModel.userData.channels[key]!!.url)

                val addOrEditItemFragment = AddOrEditItemFragment()
                addOrEditItemFragment.arguments = args
                addOrEditItemFragment.show(childFragmentManager, "EditChannelDialog")
            }

            ItemMenuFragment.ActionType.Copy -> {
                requireContext().copyUrlToClipboard(viewModel.userData.channels[key]!!.url)
            }

            ItemMenuFragment.ActionType.OpenInBrowser -> openUrl(viewModel.userData.channels[key]!!.url)
            else -> { }
        }
    }

    private fun updateData(timeoutMilliseconds: Long=0){
        requireContext().updateData(viewModel, timeoutMilliseconds){
            binding.refreshLayout.isRefreshing = false
            binding.tvEmpty.isVisible = viewModel.channelsList.value.isNullOrEmpty()
        }
    }

    private fun createChannelsAdapter(): ChannelsAdapter {
        val adapter = ChannelsAdapter()
        adapter.setOnItemClickListener(object : ChannelsAdapter.ChannelClickListener{
            override fun onClickEvent(channel: Channel) {
                if (listOf("youtube.com", "youtu.be", "vk.com", "vkvideo.ru").any { channel.url.contains(it) }){
                    val args = Bundle()
                    args.putString(ARG_REQUEST_URL, channel.url)
                    args.putString(ARG_TITLE, channel.title)
                    findNavController().navigate(R.id.action_navigation_channels_to_view_channel, args)

                }
                else openUrl(channel.url)
            }

            override fun onLongClickEvent(channel: Channel): Boolean {
                vibratePhone(50)
                openActionsMenu(channel)
                return true
            }

        })

        return adapter
    }

    private fun addChannel(){
        if (!requireContext().prepareToSaveData(viewModel))
            return

        val args = Bundle()
        args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(R.string.new_channel))
        val addOrEditItemFragment = AddOrEditItemFragment()
        addOrEditItemFragment.arguments = args
        addOrEditItemFragment.show(childFragmentManager, "AddNewChannelDialog")
    }

    private fun openActionsMenu(channel: Channel){
        val args = Bundle()
        args.putString(ItemMenuFragment.ARG_TITLE, channel.title)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_EDIT_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_COPY_URL_BUTTON, true)
        args.putBoolean(ItemMenuFragment.ARG_SHOW_OPEN_IN_BROWSER_BUTTON, true)

        val itemMenuFragment = ItemMenuFragment()
        itemMenuFragment.arguments = args
        itemMenuFragment.show(childFragmentManager, "ActionChoiceDialog")
    }
}