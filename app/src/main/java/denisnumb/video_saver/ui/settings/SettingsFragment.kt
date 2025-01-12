package denisnumb.video_saver.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.Constants.Companion.CHANNELS_CACHE
import denisnumb.video_saver.Constants.Companion.SEARCH_CACHE
import denisnumb.video_saver.Constants.Companion.USER_SETTINGS
import denisnumb.video_saver.databinding.FragmentSettingsBinding
import denisnumb.video_saver.model.RepoData
import denisnumb.video_saver.model.UserSettings
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.saveQueryCache
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showDialog
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showYesNoDialog
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.vibratePhone


class SettingsFragment : Fragment() {

    private lateinit var viewModel: SharedViewModel
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[(SharedViewModel::class.java)]
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.ddQuality.setAdapter(
            DropDownAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                arrayListOf("240p", "480p", "720p", "1080p")
            )
        )

        binding.ddReqCount.setAdapter(
            DropDownAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                arrayListOf("1", "2", "3", "4", "5", "10", "15", "20")
            )
        )

        binding.ddVideoCount.setAdapter(
            DropDownAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                arrayListOf("50", "100", "250", "500", "750", "1000", "2000")
            )
        )

        viewModel.userSettings.let{ settings ->
            settings.repoData?.let {repoData ->
                binding.etUserDataFileUrl.setText(repoData.repoUrl)
            }
            settings.token?.let {token ->
                binding.etGuthubToken.setText(token)
            }
            binding.cbMergeLocalUserData.isChecked = settings.mergeLocalDataWithCloudData
            binding.ddQuality.setText(resources.getString(R.string.quality_string, settings.videoQuality))
            binding.ddReqCount.setText(settings.requestCount.toString())
            binding.ddVideoCount.setText(settings.maxVideoCount.toString())
        }

        binding.cbMergeLocalUserData.setOnCheckedChangeListener { _, _ ->
            viewModel.userSettings.let { settings ->
                settings.mergeLocalDataWithCloudData = binding.cbMergeLocalUserData.isChecked
                saveSettings(settings, false)
                vibratePhone(50)
            }
        }

        binding.buttonSave.setOnClickListener {
            updateAndSaveGithubSettings()
            vibratePhone(50)
        }

        binding.buttonSelectDownloadsDir.setOnClickListener {
            viewModel.launchFolderPicker()
        }

        binding.ddQuality.setOnItemClickListener { _, _, _, _ ->
            updateAndSaveDropDownSettings()
        }
        binding.ddReqCount.setOnItemClickListener { _, _, _, _ ->
            updateAndSaveDropDownSettings()
        }
        binding.ddVideoCount.setOnItemClickListener { _, _, _, _ ->
            updateAndSaveDropDownSettings()
        }

        binding.buttonClearCache.setOnClickListener {
            requireContext().showYesNoDialog(resources.getString(R.string.cache_clear_confirm), {clearCache(viewModel)})
        }

        YoutubeDL.getInstance().version(requireContext())?.let{ ytDlpVersion ->
            binding.tvYtDlpVersion.text = resources.getString(R.string.yt_dlp_version, ytDlpVersion)
        }


        viewModel.downloadsDirLiveData.observe(viewLifecycleOwner){ downloadsDir ->
            downloadsDir?.path?.split(":")?.get(1)?.let { path ->
                binding.buttonSelectDownloadsDir.text = resources.getString(R.string.downloads_dir, path)
            } ?: run {
                binding.buttonSelectDownloadsDir.text = resources.getString(R.string.downloads_dir_not_selected)
            }
        }

        return binding.root
    }

    private fun clearCache(viewModel: SharedViewModel){
        requireContext().showYesNoDialog(
            resources.getString(R.string.clear_channels_cache),
            {
                viewModel.channelsCache = HashMap()
                saveQueryCache(viewModel.channelsCache, CHANNELS_CACHE)
                viewModel.searchCache = HashMap()
                saveQueryCache(viewModel.channelsCache, SEARCH_CACHE)
                requireContext().showText(resources.getString(R.string.cache_dropped))
            },
            {
                viewModel.searchCache = HashMap()
                saveQueryCache(viewModel.channelsCache, SEARCH_CACHE)
                requireContext().showText(resources.getString(R.string.cache_dropped))
            }
        )
    }

    private fun updateAndSaveDropDownSettings(){
        viewModel.userSettings.let { settings ->
            settings.videoQuality = binding.ddQuality.text.toString().replace("p", "").toInt()
            settings.requestCount = binding.ddReqCount.text.toString().toInt()
            settings.maxVideoCount = binding.ddVideoCount.text.toString().toInt()

            saveSettings(settings, false)
        }
    }

    private fun updateAndSaveGithubSettings(){
        viewModel.userSettings.let { settings ->
            var repoData: RepoData? = null
            try {
                repoData = parseRepoData(binding.etUserDataFileUrl.text.toString().lowercase())
            } catch (e: Exception){
                if (binding.etUserDataFileUrl.text.toString().isNotBlank())
                    requireContext().showText(resources.getString(R.string.invalid_url))
            }
            settings.repoData = repoData

            if (binding.etGuthubToken.text.toString().isNotBlank())
                settings.token = binding.etGuthubToken.text.toString()
            else {
                settings.token = null
                if (binding.etUserDataFileUrl.text.toString().isNotBlank())
                    requireContext().showDialog(resources.getString(R.string.no_token_info))
            }
            saveSettings(settings)
        }
    }

    private fun saveSettings(settings: UserSettings, showSavedToast: Boolean=true){
        requireActivity().getPreferences(Context.MODE_PRIVATE).edit().let { sPref ->
            sPref.putString(USER_SETTINGS, Gson().toJson(settings))
            sPref.commit()
        }
        if (showSavedToast)
            requireContext().showText(resources.getString(R.string.saved))
    }

    private fun parseRepoData(repoUrl: String): RepoData {
        if (!repoUrl.contains("github") || !repoUrl.startsWith("http"))
            throw java.lang.IllegalArgumentException("Invalid Github Url")

        val url = repoUrl.split("?")[0]
        var rawData = url.split("/").toMutableList()

        val userName = rawData[3]
        val repoName = rawData[4]

        if (rawData.contains("blob"))
            rawData.remove("blob")

        val branchName = rawData[5]
        rawData = rawData.subList(6, rawData.size)

        val filePath = rawData.joinToString("/")

        return RepoData(
            userName = userName,
            repoName = repoName,
            branchName = branchName,
            filePath = filePath,
            repoUrl = repoUrl
        )
    }
}