package com.watchvideo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.watchvideo.data.model.SearchResult

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
    var selectedTab by remember { mutableStateOf(0) }

    // 结果集变化时把选中 tab 收敛到合法范围
    LaunchedEffect(groups) {
        if (selectedTab >= groups.size) selectedTab = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { showHistory = it.isFocused && groups.isEmpty() },
                placeholder = { Text("搜索影视...") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                showHistory = false
                viewModel.search()
            }) {
                Text("搜索")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 搜索历史覆盖层
        if (showHistory && history.isNotEmpty()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("搜索历史", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                // 站点 Tab：每个数据源一个 tab，标题带结果数，知道数据从哪来
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, groups.size - 1),
                    edgePadding = 0.dp
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == selectedTab,
                            onClick = { selectedTab = index },
                            text = { Text("${group.siteName} (${group.results.size})") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val current = groups[selectedTab.coerceIn(0, groups.size - 1)]
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(current.results) { result ->
                        SearchResultItem(result = result, onClick = {
                            showHistory = false
                            onResultClick(result)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = result.cover,
                contentDescription = result.title,
                modifier = Modifier.size(80.dp, 110.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(text = result.title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
