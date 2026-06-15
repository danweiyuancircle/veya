package com.watchvideo.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchvideo.data.model.FavoriteItem
import com.watchvideo.ui.components.CircleActionButton
import com.watchvideo.ui.components.MediaListCard

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = remember { FavoritesViewModel() },
    onItemClick: (FavoriteItem) -> Unit
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
                "收藏",
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
                MediaListCard(
                    title = item.title,
                    subtitle = "源：${item.preferredSourceKey ?: "—"}",
                    coverUrl = item.coverUrl,
                    editing = editing,
                    onClick = { onItemClick(item) },
                    onDelete = { viewModel.remove(item.contentKey) },
                    extra = {
                        item.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    },
                    trailing = {
                        CircleActionButton(onClick = { onItemClick(item) }) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "已收藏",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}
