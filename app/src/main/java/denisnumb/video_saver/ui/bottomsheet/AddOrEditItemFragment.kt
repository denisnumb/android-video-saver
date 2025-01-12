package denisnumb.video_saver.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import denisnumb.video_saver.R
import denisnumb.video_saver.databinding.BottomsheetAddOrEditItemBinding

class AddOrEditItemFragment : BottomSheetDialogFragment() {
    companion object {
        const val ARG_REQUIRE_NAME = "requireName"
        const val ARG_REQUIRE_URL = "requireUrl"
        const val ARG_IS_EDIT = "edit"
        const val ARG_SET_NAME = "name"
        const val ARG_SET_URL = "url"
        const val ARG_TITLE = "title"
    }

    private lateinit var binding: BottomsheetAddOrEditItemBinding

    private var requireName: Boolean = true
    private var requireUrl: Boolean = true

    enum class AddOrEdit{
        Add, Edit
    }

    interface AddNewItemClickListener {
        fun addOrEditClickEvent(fragment: AddOrEditItemFragment, title: String, url: String, mode: AddOrEdit)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomsheetAddOrEditItemBinding.inflate(inflater, container, false)

        requireName = requireArguments().getBoolean(ARG_REQUIRE_NAME, true)
        requireUrl = requireArguments().getBoolean(ARG_REQUIRE_URL, true)
        val mode = if (requireArguments().getBoolean(ARG_IS_EDIT)) AddOrEdit.Edit else AddOrEdit.Add

        binding.buttonAdd.text = resources.getString(if (mode == AddOrEdit.Add) R.string.add else R.string.save)

        requireArguments().getString(ARG_SET_NAME)?.let {
            binding.etName.setText(it)
        }
        requireArguments().getString(ARG_SET_URL)?.let {
            binding.etUrl.setText(it)
        }

        binding.tvTitle.text = requireArguments().getString(ARG_TITLE)
        binding.etName.visibility = if (requireName) View.VISIBLE else View.GONE
        binding.etUrl.visibility = if (requireUrl) View.VISIBLE else View.GONE

        binding.buttonAdd.setOnClickListener {
            (parentFragment as? AddNewItemClickListener)?.addOrEditClickEvent(
                this,
                binding.etName.text.toString().trim(),
                binding.etUrl.text.toString().trim(),
                mode
            )
        }

        binding.etName.addTextChangedListener {it?.let {
            enableAddButton()
        }}

        binding.etUrl.addTextChangedListener {it?.let {
            enableAddButton()
        }}

        enableAddButton()

        return binding.root
    }

    private fun enableAddButton() {
        val isNameNotBlank = binding.etName.text.toString().isNotBlank()
        val isUrlValid = binding.etUrl.text.toString().startsWith("http")

        binding.buttonAdd.isEnabled = (!requireName || isNameNotBlank) && (!requireUrl || isUrlValid)
    }
}