package com.watchvideo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.watchvideo.data.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val UPDATE_APK_NAME = "update.apk"

private fun apkFile(): File = File(appContext.getExternalFilesDir(null), UPDATE_APK_NAME)

private fun authority(): String = "${appContext.packageName}.fileprovider"

actual suspend fun installUpdate(apkUrl: String, onProgress: (Float) -> Unit): InstallResult =
    withContext(Dispatchers.IO) {
        val downloaded = try {
            downloadApk(apkUrl, onProgress)
        } catch (e: Exception) {
            false
        }
        if (!downloaded) return@withContext InstallResult.FAILED

        // Android 8+ 需"安装未知应用"权限；无权限时让调用方引导授权后再装
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return@withContext InstallResult.NEED_PERMISSION
        }

        if (launchInstaller()) InstallResult.LAUNCHED else InstallResult.FAILED
    }

private suspend fun downloadApk(apkUrl: String, onProgress: (Float) -> Unit): Boolean {
    val client = createHttpClient()
    try {
        val response = client.get(apkUrl)
        val total = response.contentLength() ?: -1L
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var readTotal = 0L
        apkFile().outputStream().use { out ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                out.write(buffer, 0, read)
                readTotal += read
                onProgress(if (total > 0) readTotal.toFloat() / total else -1f)
            }
        }
        onProgress(1f)
        return true
    } finally {
        client.close()
    }
}

private fun launchInstaller(): Boolean = try {
    val uri = FileProvider.getUriForFile(appContext, authority(), apkFile())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(intent)
    true
} catch (e: Exception) {
    false
}

actual fun requestInstallPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${appContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
}

actual fun installDownloadedApk(): Boolean = launchInstaller()
