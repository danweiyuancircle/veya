package com.watchvideo.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.watchvideo.InstallResult
import com.watchvideo.data.update.UpdateInfo
import com.watchvideo.installDownloadedApk
import com.watchvideo.installUpdate
import com.watchvideo.requestInstallPermission
import kotlinx.coroutines.launch

private enum class Phase { Idle, Downloading, NeedPermission }

/**
 * 发现新版本对话框。
 * 点"更新"：Android 下载（对话框内进度条）→ 拉起系统安装；缺安装权限则引导授权后继续安装。
 * 无 apk 资产 / 下载失败 / iOS 时降级跳转 release 页。
 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(Phase.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }

    fun fallbackToWeb() {
        onDismiss()
        uriHandler.openUri(info.releaseUrl)
    }

    fun startDownload() {
        val apkUrl = info.apkDownloadUrl
        if (apkUrl == null) {
            fallbackToWeb()
            return
        }
        phase = Phase.Downloading
        progress = 0f
        scope.launch {
            when (installUpdate(apkUrl) { p -> progress = p }) {
                InstallResult.LAUNCHED -> onDismiss()
                InstallResult.NEED_PERMISSION -> phase = Phase.NeedPermission
                InstallResult.FAILED -> fallbackToWeb()
            }
        }
    }

    AlertDialog(
        // 下载中禁止点外部关闭，避免中断
        onDismissRequest = { if (phase != Phase.Downloading) onDismiss() },
        title = { Text(dialogTitle(phase, info.versionName)) },
        text = {
            when (phase) {
                Phase.Idle -> Text(info.notes.ifBlank { "有新版本可用。" })
                Phase.Downloading -> Column(modifier = Modifier.fillMaxWidth()) {
                    Text("正在下载更新…")
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", modifier = Modifier.padding(top = 4.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                }
                Phase.NeedPermission -> Text("安装需要允许「安装未知应用」权限。请点「去授权」开启后返回，再点「继续安装」。")
            }
        },
        confirmButton = {
            when (phase) {
                Phase.Idle -> TextButton(onClick = { startDownload() }) {
                    Text("更新", color = MaterialTheme.colorScheme.primary)
                }
                Phase.Downloading -> {}
                Phase.NeedPermission -> TextButton(onClick = {
                    if (installDownloadedApk()) onDismiss()
                }) {
                    Text("继续安装", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            when (phase) {
                Phase.Idle -> TextButton(onClick = onDismiss) { Text("取消") }
                Phase.Downloading -> {}
                Phase.NeedPermission -> TextButton(onClick = { requestInstallPermission() }) {
                    Text("去授权")
                }
            }
        },
    )
}

private fun dialogTitle(phase: Phase, versionName: String): String = when (phase) {
    Phase.Idle -> "发现新版本 v$versionName"
    Phase.Downloading -> "下载更新"
    Phase.NeedPermission -> "需要安装权限"
}
