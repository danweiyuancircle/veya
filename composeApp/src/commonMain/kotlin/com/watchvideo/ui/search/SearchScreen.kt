package com.watchvideo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchvideo.data.model.SearchResult
import com.watchvideo.ui.components.Cover
import com.watchvideo.ui.theme.SourceTierAvailable
import com.watchvideo.ui.theme.SourceTierOther

/** 源分级：优先 / 可用 / 其他，颜色与文案在此集中定义 */
private enum class SourceTier(val label: String, val shortLabel: String) {
    Priority("优先源", "优先"),
    Available("可用源", "可用"),
    Other("其他源", "其他"),
}

@Composable
private fun SourceTier.color(): Color = when (this) {
    SourceTier.Priority -> MaterialTheme.colorScheme.primary
    SourceTier.Available -> SourceTierAvailable
    SourceTier.Other -> SourceTierOther
}

private fun tierOf(rankValue: Double): SourceTier = when {
    rankValue >= 60.0 -> SourceTier.Priority
    rankValue > 0.0 -> SourceTier.Available
    else -> SourceTier.Other
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = remember { SearchViewModel() },
    onResultClick: (SearchResult) -> Unit
) {
    val query by viewModel.query.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val history by viewModel.history.collectAsState()

    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Veya",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { showHistory = it.isFocused && groups.isEmpty() },
            placeholder = { Text("搜索片名") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                showHistory = false
                viewModel.search()
            })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 搜索历史覆盖层
        if (showHistory && history.isNotEmpty()) {
            Column {
                Text(
                    "搜索历史",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                history.forEach { keyword ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onQueryChange(keyword)
                                showHistory = false
                                viewModel.search()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = keyword,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = { viewModel.removeHistory(keyword) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            return@Column
        }

        when {
            isLoading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
            groups.isNotEmpty() -> {
                // 按评分分级（优先/可用/其他），同档归一组；groups 不变不重算
                val tiered = remember(groups) {
                    groups
                        .map { group -> group to tierOf(viewModel.rankValueOf(group.siteKey)) }
                        .groupBy({ it.second }, { it.first })
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceTier.entries.forEach { tier ->
                        val tierGroups = tiered[tier] ?: return@forEach
                        item(key = "header_${tier.name}") {
                            TierHeader(tier = tier)
                        }
                        tierGroups.forEach { group ->
                            items(group.results, key = { "${group.siteKey}_${it.id}" }) { result ->
                                SearchResultItem(
                                    result = result,
                                    siteName = group.siteName,
                                    tier = tier,
                                    onClick = {
                                        showHistory = false
                                        onResultClick(result)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierHeader(tier: SourceTier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tier.color(), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tier.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    siteName: String,
    tier: SourceTier,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(url = result.cover, contentDescription = result.title)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "源：$siteName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TierBadge(tier = tier)
        }
    }
}

@Composable
private fun TierBadge(tier: SourceTier) {
    Box(
        modifier = Modifier
            .background(tier.color(), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = tier.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}
