package com.watchvideo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchvideo.data.local.FavoritesStore
import com.watchvideo.data.local.LocalStores
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.local.WatchHistoryStore
import com.watchvideo.currentAppVersion
import com.watchvideo.data.update.UpdateChecker
import com.watchvideo.data.update.UpdateInfo
import com.watchvideo.ui.update.UpdateDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    historyStore: WatchHistoryStore = LocalStores.history,
    favoritesStore: FavoritesStore = LocalStores.favorites,
    scoreStore: SourceScoreStore = LocalStores.sourceScore,
) {
    val uriHandler = LocalUriHandler.current
    // 待确认的危险操作：(标题, 执行体)；非空时弹确认框
    var pendingAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val version = remember { currentAppVersion() }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var noUpdate by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        SettingRow(
            Icons.Default.Update,
            if (checking) "检查更新中…" else "检查更新",
            "当前版本 $version",
        ) {
            if (checking) return@SettingRow
            checking = true
            scope.launch {
                val info = UpdateChecker().checkLatest(version)
                checking = false
                if (info != null) updateInfo = info else noUpdate = true
            }
        }

        SettingRow(Icons.Default.Code, "GitHub 仓库", "github.com/danweiyuancircle/veya") {
            uriHandler.openUri("https://github.com/danweiyuancircle/veya")
        }
        SettingRow(Icons.Default.Feedback, "问题反馈", "提交 Issue 或建议") {
            uriHandler.openUri("https://github.com/danweiyuancircle/veya/issues")
        }
        SettingRow(Icons.Default.Info, "非商业说明", "本应用仅供学习交流，非商业用途")
        SettingRow(Icons.Default.Shield, "免责声明", "内容均来自第三方，本应用不存储任何资源")
        SettingRow(Icons.Default.Gavel, "版权处理", "如有侵权请联系删除") {
            uriHandler.openUri("https://github.com/danweiyuancircle/veya/issues")
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        DangerRow("清除历史") {
            pendingAction = "清除历史" to { historyStore.clear() }
        }
        DangerRow("清除收藏") {
            pendingAction = "清除收藏" to { favoritesStore.clear() }
        }
        DangerRow("清除源评分") {
            pendingAction = "清除源评分" to { scoreStore.clear() }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "本应用仅供学习交流使用，所有视频内容均来自第三方网站，本应用不存储、不上传任何资源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "版本 $version",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    updateInfo?.let { info ->
        UpdateDialog(info = info, onDismiss = { updateInfo = null })
    }

    if (noUpdate) {
        AlertDialog(
            onDismissRequest = { noUpdate = false },
            title = { Text("检查更新") },
            text = { Text("已是最新版本") },
            confirmButton = {
                TextButton(onClick = { noUpdate = false }) { Text("确定") }
            }
        )
    }

    pendingAction?.let { (title, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text("确认$title？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    pendingAction = null
                }) {
                    Text("确认", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val rowModifier = Modifier.fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 12.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DangerRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
    }
}
