package com.watchvideo

import android.content.Intent
import androidx.core.content.FileProvider
import com.watchvideo.data.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun installUpdate(apkUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val apkFile = File(appContext.getExternalFilesDir(null), "update.apk")
        val client = createHttpClient()
        try {
            client.get(apkUrl).bodyAsChannel().let { channel ->
                apkFile.outputStream().use { out -> channel.copyTo(out) }
            }
        } finally {
            client.close()
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile,
        )
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
}
