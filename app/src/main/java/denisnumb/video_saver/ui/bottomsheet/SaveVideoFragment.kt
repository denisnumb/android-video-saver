package denisnumb.video_saver.ui.bottomsheet

import android.R
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.model.user_data_objects.VideoFolder
import denisnumb.video_saver.ui.settings.DropDownAdapter
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.prepareToSaveData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveUserData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import denisnumb.video_saver.databinding.BottomSheetSaveVideoBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isDuplicate
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.updateData

class SaveVideoFragment : BottomSheetDialogFragment(), AddOrEditItemFragment.AddNewItemClickListener {
    private lateinit var binding: BottomSheetSaveVideoBinding
    private lateinit var viewModel: SharedViewModel
    private lateinit var foldersAdapter: DropDownAdapter<String>
    private lateinit var selectedVideoFolderName: String
    private var videoIndex: Int = 0

    companion object {
        const val ARG_INDEX = "index"
        const val ARG_TITLE = "title"
        const val ARG_URL = "url"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[(SharedViewModel::class.java)]
        binding = BottomSheetSaveVideoBinding.inflate(inflater, container, false)

        videoIndex = requireArguments().getInt(ARG_INDEX)
        val title = requireArguments().getString(ARG_TITLE)!!
        val url = requireArguments().getString(ARG_URL)!!

        viewModel.videoFoldersList.observe(viewLifecycleOwner){
            updateFoldersAdapter()
        }

        binding.ddFolder.addTextChangedListener {
            binding.buttonSave.isEnabled = !binding.ddFolder.text.isNullOrEmpty()
        }

        binding.buttonSave.setOnClickListener {
            viewModel.userData.videoFolders.values.find { it.name == binding.ddFolder.text.toString() }?.let {
                selectedVideoFolderName = binding.ddFolder.text.toString()

                if (requireContext().prepareToSaveData(viewModel)){
                    val args = Bundle()
                    args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(denisnumb.video_saver.R.string.new_video))
                    args.putString(AddOrEditItemFragment.ARG_SET_NAME, title)
                    args.putString(AddOrEditItemFragment.ARG_SET_URL, url)

                    val addOrEditItemFragment = AddOrEditItemFragment()
                    addOrEditItemFragment.arguments = args
                    addOrEditItemFragment.show(childFragmentManager, "AddNewVideoDialog")
                }
            }
        }

        binding.buttonCreate.setOnClickListener {
            if (requireContext().prepareToSaveData(viewModel)){
                val args = Bundle()
                args.putString(AddOrEditItemFragment.ARG_TITLE, resources.getString(denisnumb.video_saver.R.string.new_video_folder))
                args.putBoolean(AddOrEditItemFragment.ARG_REQUIRE_URL, false)

                val addOrEditItemFragment = AddOrEditItemFragment()
                addOrEditItemFragment.arguments = args
                addOrEditItemFragment.show(childFragmentManager, "AddNewFolderDialog")
            }
        }

        return binding.root
    }

    override fun addOrEditClickEvent(
        fragment: AddOrEditItemFragment,
        title: String,
        url: String,
        mode: AddOrEditItemFragment.AddOrEdit
    ) {
        if (url.isEmpty()){
            if (viewModel.userData.videoFolders.containsKey(title))
                return requireContext().showText(resources.getString(denisnumb.video_saver.R.string.folder_already_exists))
            fragment.dismiss()

            viewModel.userData.videoFolders[title] = VideoFolder(title, mutableListOf())
            requireContext().saveUserData(viewModel, "[+] Видео-папка: $title")
            requireContext().updateData(viewModel, 1000)
            return requireContext().showText(resources.getString(denisnumb.video_saver.R.string.saved))
        }

        val selectedVideoFolder = viewModel.userData.videoFolders[selectedVideoFolderName]!!
        selectedVideoFolder.videos.let { videos ->
            if (videos.any { it.isDuplicate(title, url) })
                return requireContext().showText(resources.getString(denisnumb.video_saver.R.string.video_already_exists))
        }
        fragment.dismiss()

        selectedVideoFolder.videos.add(FullVideoData(title, url))
        requireContext().saveUserData(viewModel, "[+] Видео: $title")
        requireContext().updateData(viewModel, 1000)
        requireContext().showText(resources.getString(denisnumb.video_saver.R.string.saved))
        dismiss()
    }

    private fun updateFoldersAdapter() {
        foldersAdapter = DropDownAdapter(
            requireContext(),
            R.layout.simple_list_item_1,
            viewModel.userData.videoFolders.keys.toList()
        )
        binding.ddFolder.setAdapter(foldersAdapter)
    }
}