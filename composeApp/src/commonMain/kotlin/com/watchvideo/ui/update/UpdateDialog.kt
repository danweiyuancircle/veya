package com.watchvideo.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.watchvideo.data.update.UpdateInfo
import com.watchvideo.installUpdate
import kotlinx.coroutines.launch

/** 发现新版本对话框。点"更新"：Android 下载安装 apk，失败或无 apk 资产则降级跳转 release 页（iOS 总是跳转）。 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.versionName}") },
        text = { Text(info.notes.ifBlank { "有新版本可用。" }) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                scope.launch {
                    val apkUrl = info.apkDownloadUrl
                    val installed = apkUrl != null && installUpdate(apkUrl)
                    if (!installed) uriHandler.openUri(info.releaseUrl)
                }
            }) {
                Text("更新", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
