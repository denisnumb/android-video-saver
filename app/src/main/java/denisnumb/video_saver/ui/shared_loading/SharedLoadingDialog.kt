package denisnumb.video_saver.ui.shared_loading

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import denisnumb.video_saver.R
import denisnumb.video_saver.databinding.SharedLoadingDialogBinding
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText

class SharedLoadingDialog : DialogFragment() {
    private lateinit var viewModel: SharedLoadingViewModel
    private var _binding: SharedLoadingDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = SharedLoadingDialogBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(requireActivity())[SharedLoadingViewModel::class.java]

        binding.cancelBtn.setOnClickListener {
            viewModel.cancelLoading()
            requireContext().showText(getString(R.string.loading_canceled))
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.setCancelable(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
