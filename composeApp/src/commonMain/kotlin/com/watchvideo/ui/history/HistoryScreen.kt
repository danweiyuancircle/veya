package com.watchvideo.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchvideo.data.model.WatchHistoryItem
import com.watchvideo.ui.components.CircleActionButton
import com.watchvideo.ui.components.MediaListCard

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = remember { HistoryViewModel() },
    onItemClick: (WatchHistoryItem) -> Unit
) {
    val list by viewModel.list.collectAsState()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "历史",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "完成" else "编辑")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.contentKey }) { item ->
                val percent = if (item.durationMs > 0) {
                    (item.progressMs.toDouble() / item.durationMs.toDouble() * 100).toInt().coerceIn(0, 100)
                } else null
                MediaListCard(
                    title = item.title,
                    subtitle = "源：${item.sourceKey}　上次 ${item.episodeLabel}",
                    coverUrl = item.coverUrl,
                    editing = editing,
                    onClick = { onItemClick(item) },
                    onDelete = { viewModel.remove(item.contentKey) },
                    extra = {
                        if (percent != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "已观看 $percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailing = {
                        CircleActionButton(onClick = { onItemClick(item) }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}
