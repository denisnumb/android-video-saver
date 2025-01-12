package denisnumb.video_saver.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import denisnumb.video_saver.databinding.BottomsheetItemMenuBinding

class ItemMenuFragment : BottomSheetDialogFragment() {
    private lateinit var binding: BottomsheetItemMenuBinding

    companion object {
        const val ARG_KEY = "key"
        const val ARG_TITLE = "title"
        const val ARG_INDEX = "index"
        const val ARG_SHOW_REMOVE_BUTTON = "remove"
        const val ARG_SHOW_EDIT_URL_BUTTON = "edit"
        const val ARG_SHOW_COPY_URL_BUTTON = "copy"
        const val ARG_SHOW_OPEN_IN_BROWSER_BUTTON = "openInBrowser"
        const val ARG_SHOW_REMOVE_FROM_CACHE_BUTTON = "removeFromCache"
        const val ARG_SHOW_OPEN_CHANNEL_BUTTON = "openChannel"
        const val ARG_SHOW_SAVE_VIDEO_BUTTON = "saveVideo"
        const val ARG_SHOW_DOWNLOAD_VIDEO_BUTTON = "downloadVideo"
        const val ARG_SHOW_DELETE_DOWNLOADED_VIDEO_BUTTON = "deleteDownloadedVideo"
        const val ARG_SHOW_CANCEL_DOWNLOADING_BUTTON = "cancelDownloading"
    }

    interface ActionChoiceEvent {
        fun actionChoice(actionType: ActionType, key: String, position: Int)
    }

    enum class ActionType {
        Remove, Edit, Copy,
        OpenInBrowser, RemoveFromCache, OpenChannel,
        SaveVideo, DownloadVideo, DeleteDownloadedVideo, CancelDownloading
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomsheetItemMenuBinding.inflate(inflater, container, false)

        val title = requireArguments().getString(ARG_TITLE)!!
        val key = requireArguments().getString(ARG_KEY, title)
        val position = requireArguments().getInt(ARG_INDEX, 0)

        binding.tvTitle.text = title
        binding.buttonEdit.isVisible = requireArguments().getBoolean(ARG_SHOW_EDIT_URL_BUTTON)
        binding.buttonCopy.isVisible = requireArguments().getBoolean(ARG_SHOW_COPY_URL_BUTTON)
        binding.buttonRemove.isVisible = requireArguments().getBoolean(ARG_SHOW_REMOVE_BUTTON, true)
        binding.buttonOpenInBrowser.isVisible = requireArguments().getBoolean(ARG_SHOW_OPEN_IN_BROWSER_BUTTON)
        binding.buttonRemoveFromCache.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_REMOVE_FROM_CACHE_BUTTON)) View.VISIBLE else View.GONE
        binding.buttonOpenChannel.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_OPEN_CHANNEL_BUTTON)) View.VISIBLE else View.GONE
        binding.buttonSaveVideo.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_SAVE_VIDEO_BUTTON)) View.VISIBLE else View.GONE
        binding.buttonDownloadVideo.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_DOWNLOAD_VIDEO_BUTTON)) View.VISIBLE else View.GONE
        binding.buttonDeleteDownloadedVideo.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_DELETE_DOWNLOADED_VIDEO_BUTTON)) View.VISIBLE else View.GONE
        binding.buttonCancelDownloading.visibility = if (requireArguments().getBoolean(
                ARG_SHOW_CANCEL_DOWNLOADING_BUTTON)) View.VISIBLE else View.GONE

        binding.buttonRemove.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.Remove, key, position)
            dismiss()
        }
        binding.buttonEdit.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.Edit, key, position)
            dismiss()
        }
        binding.buttonCopy.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.Copy, key, position)
            dismiss()
        }
        binding.buttonOpenInBrowser.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.OpenInBrowser, key, position)
            dismiss()
        }
        binding.buttonRemoveFromCache.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.RemoveFromCache, key, position)
            dismiss()
        }
        binding.buttonOpenChannel.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.OpenChannel, key, position)
            dismiss()
        }
        binding.buttonSaveVideo.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.SaveVideo, key, position)
            dismiss()
        }
        binding.buttonDownloadVideo.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.DownloadVideo, key, position)
            dismiss()
        }
        binding.buttonDeleteDownloadedVideo.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.DeleteDownloadedVideo, key, position)
            dismiss()
        }
        binding.buttonCancelDownloading.setOnClickListener {
            (parentFragment as? ActionChoiceEvent)?.actionChoice(ActionType.CancelDownloading, key, position)
            dismiss()
        }

        return binding.root
    }
}