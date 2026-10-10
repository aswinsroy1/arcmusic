package com.aeswox.arcmusic.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.aeswox.arcmusic.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String?,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

class UpdateManager(private val context: Context) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    private val api = retrofit.create(GitHubApi::class.java)

    private var lastDownloadId: Long = -1L

    fun installDownloadedUpdate() {
        if (lastDownloadId != -1L) installApk(context, lastDownloadId)
    }

    /**
     * Release source for the in-app updater. Kept as defaults so the account and
     * repo live in one place — see [GitHubApi.getLatestRelease].
     */
    suspend fun checkForUpdates(
        owner: String = "aesw0x",
        repo: String = "arcmusic"
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val release = api.getLatestRelease(owner, repo)
            val latestVersion = release.tag_name.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (isNewerVersion(latestVersion, currentVersion)) {
                val apkUrl = getBestApkUrl(release.assets)
                if (apkUrl != null) {
                    return@withContext UpdateResult.UpdateAvailable(
                        version = latestVersion,
                        changelog = release.body ?: "No changelog provided.",
                        downloadUrl = apkUrl
                    )
                }
            }
            return@withContext UpdateResult.NoUpdate
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun getBestApkUrl(assets: List<GitHubAsset>): String? {
        val supportedAbis = Build.SUPPORTED_ABIS
        var bestMatch: String? = null
        
        for (asset in assets) {
            if (!asset.name.endsWith(".apk")) continue
            
            // Check for exact architecture match (arm64-v8a, armeabi-v7a, x86, etc)
            if (supportedAbis.any { asset.name.contains(it) }) {
                return asset.browser_download_url
            }
            
            // Fallback to universal package if present
            if (asset.name.contains("universal")) {
                bestMatch = asset.browser_download_url
            }
        }
        
        return bestMatch ?: assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url
    }

    fun downloadAndInstall(url: String, version: String, onProgress: (Float) -> Unit = {}, onCompleteCallback: () -> Unit = {}) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Update")
            .setDescription("Version $version is downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (file.exists()) file.delete()

        val downloadId = downloadManager.enqueue(request)
        lastDownloadId = downloadId

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        onCompleteCallback()
                        installApk(context, downloadId)
                        context.unregisterReceiver(this)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        // Poll progress
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusColumn >= 0) {
                        val status = cursor.getInt(statusColumn)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                        } else if (status == DownloadManager.STATUS_RUNNING) {
                            val downloadedColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            
                            if (downloadedColumn >= 0 && totalColumn >= 0) {
                                val downloaded = cursor.getLong(downloadedColumn)
                                val total = cursor.getLong(totalColumn)
                                if (total > 0) {
                                    val progress = downloaded.toFloat() / total.toFloat()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                }
                cursor?.close()
                if (isDownloading) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    private fun installApk(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}

sealed class UpdateResult {
    object Checking : UpdateResult()
    data class UpdateAvailable(val version: String, val changelog: String, val downloadUrl: String) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object ReadyToInstall : DownloadState()
}
