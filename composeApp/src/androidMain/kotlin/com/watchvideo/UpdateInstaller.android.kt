package com.watchvideo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    // 用独立 OkHttp 客户端：跟随重定向（GitHub→S3），二进制下载不经 gzip 干扰，
    // Content-Length 可靠。进度回调切回主线程驱动 Compose 重组。
    val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
    val response = client.newCall(Request.Builder().url(apkUrl).build()).execute()
    response.use { resp ->
        val body = resp.body ?: return false
        if (!resp.isSuccessful) return false
        val total = body.contentLength() // 已是最终重定向后的实体长度
        val buffer = ByteArray(64 * 1024)
        var readTotal = 0L
        var lastPct = -1
        body.byteStream().use { input ->
            apkFile().outputStream().use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    readTotal += read
                    if (total > 0) {
                        // 节流：百分比整数变化才回调，避免每 64KB 都切主线程拖慢下载
                        val pct = (readTotal * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            withContext(Dispatchers.Main) { onProgress(pct / 100f) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { onProgress(-1f) }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) { onProgress(1f) }
        return true
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
