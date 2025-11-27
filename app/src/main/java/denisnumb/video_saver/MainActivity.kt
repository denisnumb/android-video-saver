package denisnumb.video_saver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import denisnumb.video_saver.Constants.Companion.CHANNELS_CACHE
import denisnumb.video_saver.Constants.Companion.SEARCH_CACHE
import denisnumb.video_saver.Constants.Companion.SEARCH_QUERIES
import denisnumb.video_saver.Constants.Companion.SHARED_URL
import denisnumb.video_saver.Constants.Companion.USER_SETTINGS
import denisnumb.video_saver.databinding.ActivityMainBinding
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.model.UserSettings
import denisnumb.video_saver.model.VideoData
import denisnumb.video_saver.model.responses.FullVideoDataResponse
import denisnumb.video_saver.model.responses.Response
import denisnumb.video_saver.model.responses.ResponseStatus
import denisnumb.video_saver.ui.shared_loading.SharedLoadingDialog
import denisnumb.video_saver.ui.shared_loading.SharedLoadingViewModel
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.MD5
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.getDirectoryFilesList
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.getDownloadPath
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.handleResponseError
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.isInternetAvailable
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.openInVideoPlayer
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showDialog
import denisnumb.video_saver.utils.ExtensionFunctions.Companion.showText
import denisnumb.video_saver.utils.YdlRequestUtils
import denisnumb.video_saver.utils.YdlRequestUtils.Companion.isSrcUrlAlive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: SharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e("ERROR", "failed to initialize yt-dlp", e)
        }

        val context = this
        if (isInternetAvailable(context)){
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)?.let { status ->
                        if (status == YoutubeDL.UpdateStatus.DONE)
                            showText("yt-dlp updated successfully")
                    }
                } catch (e: YoutubeDLException) {
                    showText("Failed to update yt-dlp")
                    Log.d("ERROR", "Failed to update yt-dlp", e)
                }
            }
        }

        viewModel = ViewModelProvider(this)[SharedViewModel::class.java]

        val preferences = getPreferences(MODE_PRIVATE)
        preferences.getString(USER_SETTINGS, null)?.let { userSettingsJson ->
            viewModel.userSettings = Gson().fromJson(userSettingsJson, UserSettings::class.java)
        }
        preferences.getString(SEARCH_QUERIES, null)?.let { queries ->
            viewModel.searchQueries = Gson().fromJson(queries, HashMap<String, String>()::class.java)
        }
        preferences.getString(CHANNELS_CACHE, null)?.let { cache ->
            val type = object : TypeToken<HashMap<String, LinkedHashSet<String>>>(){}.type
            viewModel.channelsCache = Gson().fromJson(cache, type)
        }
        preferences.getString(SEARCH_CACHE, null)?.let { cache ->
            val type = object : TypeToken<HashMap<String, LinkedHashSet<String>>>(){}.type
            viewModel.searchCache = Gson().fromJson(cache, type)
        }

        val videoCacheLatch = CountDownLatch(1)
        CoroutineScope(Dispatchers.IO).launch {
            preferences.getString(Constants.VIDEO_CACHE, null)?.let { cache ->
                val type = object : TypeToken<HashMap<String, FullVideoData>>(){}.type
                viewModel.videoCache = Gson().fromJson(cache, type)

                if (viewModel.videoCache.isNotEmpty()){
                    var removeSourceUrl = false
                    viewModel.videoCache.values.find { !it.sourceUrl.isNullOrEmpty() }?.let { firstVideoWithSrcUrl ->
                        removeSourceUrl = !isSrcUrlAlive(firstVideoWithSrcUrl.sourceUrl)
                    }

                    for (video in viewModel.videoCache.values){
                        video.sourceUrl = if (removeSourceUrl) null else video.sourceUrl
                    }
                }
            }
            videoCacheLatch.countDown()
        }

        viewModel.registerLauncher(this)
        getDownloadPath(viewModel, false)

        viewModel.downloadsDirLiveData.observe(this){
            it?.let { downloadsDir ->
                viewModel.downloadedHashes = getDirectoryFilesList(downloadsDir).mapNotNull { it.name }.toMutableSet()
            }
        }

        videoCacheLatch.await()
        viewModel.loadData(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_channels,
                R.id.navigation_video_folders,
                R.id.navigation_search,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId){
                R.id.navigation_channels -> {
                    navController.navigate(R.id.navigation_channels)
                    true
                }
                R.id.navigation_video_folders -> {
                    navController.navigate(R.id.navigation_video_folders)
                    true
                }
                R.id.navigation_search -> {
                    navController.navigate(R.id.navigation_search)
                    true
                }
                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings)
                    true
                }
                else -> false
            }
        }

        navView.setOnItemReselectedListener { menuItem ->
            if (menuItem.itemId == R.id.navigation_video_folders){
                if (navController.currentDestination?.id == R.id.navigation_videos)
                    navController.popBackStack()
            }
        }

        intent.getStringExtra(SHARED_URL)?.let { sharedUrl ->
            lifecycleScope.launch {
                loadSharedUrl(sharedUrl)
            }
        }
    }

    private val sharedLoadingViewModel: SharedLoadingViewModel by viewModels()
    private var loadingDialog: SharedLoadingDialog? = null

    private fun loadSharedUrl(url: String) {
        intent.removeExtra(SHARED_URL)
        sharedLoadingViewModel.loadVideoByUrl(url, this, viewModel)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedLoadingViewModel.isLoading.collect { isLoading ->
                    if (isLoading && loadingDialog == null) {
                        loadingDialog = SharedLoadingDialog().also { dialog ->
                            dialog.show(supportFragmentManager, "loading")
                        }
                    } else if (!isLoading) {
                        loadingDialog?.dismiss()
                        loadingDialog = null
                    }
                }
            }
        }
    }
}