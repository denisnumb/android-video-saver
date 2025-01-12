package denisnumb.video_saver.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import denisnumb.video_saver.Constants.Companion.DOWNLOADS_DIR
import denisnumb.video_saver.Constants.Companion.USER_DATA
import denisnumb.video_saver.R
import denisnumb.video_saver.SharedViewModel
import denisnumb.video_saver.Constants.Companion.SEARCH_QUERIES
import denisnumb.video_saver.Constants.Companion.VIDEO_CACHE
import denisnumb.video_saver.model.FullVideoData
import denisnumb.video_saver.model.responses.Response
import denisnumb.video_saver.model.responses.ResponseStatus
import denisnumb.video_saver.model.user_data_objects.IUserDataObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

class ExtensionFunctions {
    companion object {
        @Suppress("DEPRECATION")
        fun Fragment.vibratePhone(vibrationTimeMilliseconds: Long) {
            val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(vibrationTimeMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(vibrationTimeMilliseconds)
            }
        }

        fun Context.showText(message: CharSequence, textLengthType: Int= Toast.LENGTH_SHORT){
            val context = this
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, message, textLengthType).show()
            }
        }

        fun Context.handleResponseError(response: Response){
            when (response.status){
                ResponseStatus.NOT_AVAILABLE -> {
                    showText(resources.getString(R.string.unavailible_video), Toast.LENGTH_LONG)
                }
                ResponseStatus.NOT_FOUND -> {
                    showText(resources.getString(R.string.video_not_found_404))
                }
                ResponseStatus.CANCELED -> {
                    showText(resources.getString(R.string.loading_canceled))
                }
                ResponseStatus.ERROR -> {
                    response.message?.let { error ->
                        showDialog(
                            resources.getString(R.string.video_data_getting_error_string, error),
                        )
                    } ?: {
                        response.exitCode?.let {code ->
                            showText(resources.getString(R.string.video_data_getting_error_code, code.toString()))
                        }
                    }
                }
                ResponseStatus.OK -> {}
            }
        }

        fun IUserDataObject.isDuplicate(name: String, url: String): Boolean{
            return this.url.replace("https", "http") == url.replace("https", "http")
                    || this.title.lowercase() == name.lowercase()
        }

        fun Fragment.openUrl(url: String){
            try{
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (ex: Exception){
                requireContext().showDialog(resources.getString(R.string.open_url_error, url, ex.message))
            }
        }

        fun Fragment.openInVideoPlayer(url: String) = openInVideoPlayer(Uri.parse(url))

        fun Fragment.openInVideoPlayer(uri: Uri?){
            if (uri == null)
                return requireContext().showText(resources.getString(R.string.file_not_found))
            try{
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri,"video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (ex: Exception){
                requireContext().showDialog(resources.getString(R.string.open_url_error, uri, ex.message))
            }
        }

        fun Activity.saveDownloadPath(path: String?){
            getPreferences(MODE_PRIVATE).edit().putString(DOWNLOADS_DIR, path).apply()
        }

        fun Activity.requestUriPermissions(uri: Uri): Boolean{
            return try{
                val persistedPermissions = this.contentResolver.persistedUriPermissions
                val hasPermission = persistedPermissions.any { it.uri == uri }

                if (!hasPermission) {
                    this.contentResolver.takePersistableUriPermission(
                        uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                true
            } catch (_: Exception){
                false
            }
        }

        fun Activity.getDownloadPath(viewModel: SharedViewModel, requestPathChoice: Boolean=true): Uri? {
            getPreferences(MODE_PRIVATE).getString(DOWNLOADS_DIR, null)?.let { path ->
                val uri = Uri.parse(path)
                if (requestUriPermissions(uri)){
                    viewModel.updateDownloadDirLiveData(uri)
                    return uri
                } else {
                    viewModel.updateDownloadDirLiveData(null)
                    saveDownloadPath(null)
                    return null
                }
            } ?: run {
                if (requestPathChoice)
                    viewModel.launchFolderPicker()
                return null
            }
        }

        fun Context.getDirectoryFilesList(directoryUri: Uri): List<DocumentFile>{
            return DocumentFile.fromTreeUri(this, directoryUri)?.listFiles()?.toList() ?: emptyList()
        }

        private fun Activity.getDownloads(viewModel: SharedViewModel): List<DocumentFile> {
            return getDownloadPath(viewModel, false)?.let { path ->
                getDirectoryFilesList(path)
            } ?: emptyList()
        }

        fun Activity.getDownloadedVideoFile(viewModel: SharedViewModel, video: FullVideoData): DocumentFile?
            = getDownloads(viewModel).find { it.name == video.hash }


        fun Activity.deleteDownloadedVideo(viewModel: SharedViewModel, video: FullVideoData){
            viewModel.downloadedHashes.remove(video.hash)
            getDownloadedVideoFile(viewModel, video)?.delete()
        }

        fun isInternetAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }

        fun Context.createFileInDirectory(directoryUri: Uri, fileName: String, mimeType: String="application/octet-stream"): Uri? {
            return try {
                DocumentFile.fromTreeUri(this, directoryUri)?.createFile(mimeType, fileName)?.uri
            } catch (e: Exception) {
                null
            }
        }

        fun Context.prepareToSaveData(viewModel: SharedViewModel): Boolean {
            if (viewModel.userSettings.repoData != null && isInternetAvailable(this)){
                if (viewModel.userSettings.token.isNullOrEmpty()){
                    showText(resources.getString(R.string.require_github_token), Toast.LENGTH_LONG)
                    return false
                }
                if (viewModel.githubFileData == null){
                    showDialog(resources.getString(R.string.github_file_is_null, viewModel.userSettings.repoData!!.filePath))
                    return false
                }
            }
            return true
        }

        fun Context.showDialog(message: String){
            val context = this
            CoroutineScope(Dispatchers.Main).launch {
                AlertDialog.Builder(context).setMessage(message).show()
            }
        }

        fun Context.showYesNoDialog(message: String, actionYes: () -> Unit, actionNo: () -> Unit={}){
            val context = this
            AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(context.resources.getString(R.string.yes)) { p0, _ ->
                    p0.dismiss()
                    actionYes()
                }
                .setNegativeButton(context.resources.getString(R.string.no)) { p0, _ ->
                    p0.dismiss()
                    actionNo()
                }
                .show()
        }

        fun Context.copyUrlToClipboard(url: String){
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", url))
            showText(resources.getString(R.string.copied))
        }

        fun MD5(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())

            return digest.joinToString("") { "%02x".format(it) }
        }

        fun Context.updateData(viewModel: SharedViewModel, timeoutMilliseconds: Long=0, doAfter: () -> Unit={}){
            val context = this
            val delayBeforeRequest = if (viewModel.userSettings.repoData == null) 50 else timeoutMilliseconds

            CoroutineScope(Dispatchers.IO).launch {
                delay(delayBeforeRequest)
                viewModel.loadData(context)

                CoroutineScope(Dispatchers.Main).launch{
                    doAfter()
                }
            }
        }

        fun Context.saveUserData(viewModel: SharedViewModel, commitMessage: String){
            if (isInternetAvailable(this)){
                viewModel.userSettings.repoData?.let { repoData ->
                    UserDataUtils.saveUserData(
                        this,
                        repoData,
                        viewModel.githubFileData!!,
                        commitMessage,
                        viewModel.userData,
                        viewModel.userSettings.token!!
                    )
                }
            }
            saveUserDataLocal(viewModel)
        }

        fun Context.saveUserDataLocal(viewModel: SharedViewModel){
            val context = (this as Activity)
            CoroutineScope(Dispatchers.IO).launch {
                context.getPreferences(MODE_PRIVATE).edit().let { sPref ->
                    sPref.putString(USER_DATA, UserDataUtils.convertUserDataToJson(viewModel.userData))
                    sPref.apply()
                }
            }
        }

        fun Fragment.saveQueryCache(cache: HashMap<String, LinkedHashSet<String>>, key: String){
            CoroutineScope(Dispatchers.IO).launch {
                requireActivity().getPreferences(MODE_PRIVATE).edit().let { sPref ->
                    sPref.putString(key, Gson().toJson(cache))
                    sPref.apply()
                }
            }
        }

        fun Fragment.saveVideoCache(cache: HashMap<String, FullVideoData>){
            CoroutineScope(Dispatchers.IO).launch {
                requireActivity().getPreferences(MODE_PRIVATE).edit().let { sPref ->
                    sPref.putString(VIDEO_CACHE, Gson().toJson(cache))
                    sPref.apply()
                }
            }
        }

        fun Fragment.saveSearchQueries(viewModel: SharedViewModel){
            CoroutineScope(Dispatchers.IO).launch {
                requireActivity().getPreferences(MODE_PRIVATE).edit().let { sPref ->
                    sPref.putString(SEARCH_QUERIES, Gson().toJson(viewModel.searchQueries))
                    sPref.apply()
                }
            }
        }
    }
}