package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val isMandatory: Boolean = false,
    val fileSizeBytes: Long = 0L
)

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateUiState
    data class UpToDate(val checkedTimestamp: Long = System.currentTimeMillis()) : UpdateUiState
    data class Downloading(
        val info: AppUpdateInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class ReadyToInstall(val info: AppUpdateInfo, val apkFile: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

object MaxUpdateService {

    /**
     * Checks remote JSON manifest for a newer app version.
     * Expects a JSON format like:
     * {
     *   "versionCode": 2,
     *   "versionName": "1.1",
     *   "downloadUrl": "https://example.com/max-ai-v1.1.apk",
     *   "changelog": "• New Expressive Voice presets\n• Advanced Conversational Brain",
     *   "isMandatory": false,
     *   "fileSizeBytes": 15420000
     * }
     */
    suspend fun checkRemoteUpdateManifest(
        manifestUrl: String,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            if (manifestUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Manifest URL is empty"))
            }

            val url = URL(manifestUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MAX-AI-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Server returned HTTP $responseCode")
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val remoteVersionCode = json.optInt("versionCode", 0)
            val remoteVersionName = json.optString("versionName", "1.0")
            val downloadUrl = json.optString("downloadUrl", "")
            val changelog = json.optString("changelog", "Bug fixes and performance improvements.")
            val isMandatory = json.optBoolean("isMandatory", false)
            val fileSizeBytes = json.optLong("fileSizeBytes", 0L)

            if (downloadUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Update manifest does not contain a downloadUrl")
                )
            }

            if (remoteVersionCode > currentVersionCode) {
                Result.success(
                    AppUpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = remoteVersionName,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        isMandatory = isMandatory,
                        fileSizeBytes = fileSizeBytes
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file to app cache and reports progress.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(updatesDir, "max_ai_update_v${updateInfo.versionCode}.apk")

            val url = URL(updateInfo.downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MAX-AI-Android/${BuildConfig.VERSION_NAME}")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                ?: updateInfo.fileSizeBytes.takeIf { it > 0 }
                ?: -1L

            var bytesDownloaded = 0L
            val buffer = ByteArray(8 * 1024)

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(destinationFile).use { output ->
                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(percent, bytesDownloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            onProgress(100, bytesDownloaded, totalBytes)
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers the Android package installer to install the downloaded APK file.
     */
    fun launchApkInstallation(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(IllegalStateException("APK file does not exist: ${apkFile.absolutePath}"))
            }

            // On Android 8.0+ (Oreo), check if app has permission to install unknown apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
