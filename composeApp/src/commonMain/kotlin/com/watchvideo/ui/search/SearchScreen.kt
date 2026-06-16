package com.watchvideo.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.watchvideo.ui.theme.SourceTier
import com.watchvideo.ui.theme.color
import com.watchvideo.ui.theme.tierOf
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = SearchViewModelHolder.instance,
    onResultClick: (SearchResult) -> Unit
) {
    val query by viewModel.query.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val warning by viewModel.warning.collectAsState()
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

        // 有结果但部分源失败时的提示（可诊断哪个源不可用）
        warning?.let { w ->
            Text(
                text = "⚠ $w",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
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
                // 横向 tab 展示各源，groups 已按评分降序排好（评分高的源在最前/第一个 tab）
                val pagerState = rememberPagerState(pageCount = { groups.size })
                val scope = rememberCoroutineScope()

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.size - 1),
                    edgePadding = 0.dp,
                ) {
                    groups.forEachIndexed { index, group ->
                        val tier = tierOf(viewModel.rankValueOf(group.siteKey))
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(tier.color(), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(group.siteName)
                                }
                            },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val group = groups[page]
                    val tier = tierOf(viewModel.rankValueOf(group.siteKey))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
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
