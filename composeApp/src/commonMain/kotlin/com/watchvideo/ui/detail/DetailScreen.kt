package com.watchvideo.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.watchvideo.data.AggregatedDetailService
import com.watchvideo.data.local.FavoritesStore
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.local.WatchHistoryStore
import com.watchvideo.data.model.AggregatedDetail
import com.watchvideo.platformEpochMs
import com.watchvideo.ui.theme.SourceTier
import com.watchvideo.ui.theme.color
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    siteKey: String,
    vodId: String,
    title: String,
    viewModel: DetailViewModel = remember {
        val settings = Settings()
        val scoreStore = SourceScoreStore(settings, nowEpochMsProvider = { platformEpochMs() })
        DetailViewModel(
            aggregatedDetailService = AggregatedDetailService(scoreStore),
            historyStore = WatchHistoryStore(settings),
            favoritesStore = FavoritesStore(settings),
            scoreStore = scoreStore,
        )
    },
    onBack: () -> Unit,
) {
    LaunchedEffect(siteKey, vodId) {
        viewModel.loadDetail(siteKey, vodId, title)
    }

    val state by viewModel.state.collectAsState()
    val streamUrl by viewModel.streamUrl.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val autoSelectSource by viewModel.autoSelectSource.collectAsState()
    val detail by viewModel.detail.collectAsState()
    var isFullscreen by remember { mutableStateOf(false) }

    // 当前选中三元组：从稳定的 detail + 当前状态的选中 keys 推导，
    // 不依赖捕获瞬态 Ready，避免自动播放跳过 Ready 后选集区不渲染。
    val selection = currentSelection(state, detail)

    val resolutionHeight = (state as? DetailPlaybackState.Playing)?.resolutionHeight

    // 分辨率角标只在拿到/变更分辨率后短暂显示，3 秒后自动淡出，不常驻
    var showResolution by remember { mutableStateOf(false) }
    LaunchedEffect(resolutionHeight) {
        if (resolutionHeight != null) {
            showResolution = true
            delay(3000)
            showResolution = false
        }
    }

    val onPrev: (() -> Unit)? = selection?.let { sel ->
        val routeKey = sel.routeKey ?: return@let null
        sel.prevEpisodeKey?.let { prevKey ->
            { viewModel.selectEpisode(sel.sourceKey, routeKey, prevKey) }
        }
    }
    val onNext: (() -> Unit)? = selection?.let { sel ->
        val routeKey = sel.routeKey ?: return@let null
        sel.nextEpisodeKey?.let { nextKey ->
            { viewModel.selectEpisode(sel.sourceKey, routeKey, nextKey) }
        }
    }

    // 单一 Column，VideoPlayerArea 始终存在于同一 composition slot，避免全屏切换时销毁重建播放器。
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            VideoPlayerArea(
                m3u8Url = streamUrl,
                title = selection?.detail?.title ?: title,
                isFullscreen = isFullscreen,
                onFullscreenToggle = { isFullscreen = !isFullscreen },
                onBack = onBack,
                onPrevEpisode = onPrev,
                onNextEpisode = onNext,
                onResolutionObserved = viewModel::onResolutionObserved,
                modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
            val resolutionAlpha by animateFloatAsState(
                targetValue = if (resolutionHeight != null && showResolution) 1f else 0f,
                label = "resolutionAlpha",
            )
            if (resolutionHeight != null && resolutionAlpha > 0f) {
                Text(
                    text = "${resolutionHeight}p",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { alpha = resolutionAlpha }
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        if (!isFullscreen) {
            when (state) {
                DetailPlaybackState.LoadingDetail, DetailPlaybackState.Idle -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is DetailPlaybackState.Failed -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (state as DetailPlaybackState.Failed).message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> if (selection != null) {
                    DetailContent(
                        selection = selection,
                        isFavorite = isFavorite,
                        autoSelectSource = autoSelectSource,
                        sourceTier = viewModel::sourceTier,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSetAutoSelect = viewModel::setAutoSelectSource,
                        onSelectSource = viewModel::selectSource,
                        onSelectRoute = viewModel::selectRoute,
                        onSelectEpisode = viewModel::selectEpisode,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 当前选中视图模型：detail + 选中三元组 + 前/后集 key。 */
private class Selection(
    val detail: AggregatedDetail,
    val sourceKey: String,
    val routeKey: String?,
    val episodeKey: String?,
    val prevEpisodeKey: String?,
    val nextEpisodeKey: String?,
)

private fun currentSelection(
    state: DetailPlaybackState,
    detail: AggregatedDetail?,
): Selection? {
    val aggregated = detail ?: return null
    val keys: SelectionKeys = when (state) {
        is DetailPlaybackState.Ready ->
            SelectionKeys(aggregated, state.selectedSourceKey, state.selectedRouteKey, state.selectedEpisodeKey)
        is DetailPlaybackState.ResolvingStream ->
            SelectionKeys(aggregated, state.sourceKey, state.routeKey, state.episodeKey)
        is DetailPlaybackState.Buffering ->
            SelectionKeys(aggregated, state.sourceKey, state.routeKey, state.episodeKey)
        is DetailPlaybackState.Playing ->
            SelectionKeys(aggregated, state.sourceKey, state.routeKey, state.episodeKey)
        is DetailPlaybackState.Recovering ->
            // 恢复中：保留聚合详情，选中三元组留空（下一候选确定后会进入具体播放态）
            SelectionKeys(aggregated, aggregated.sourceDetails.first().sourceKey, null, null)
        else -> null
    } ?: return null
    val detail = keys.detail
    val sourceKey = keys.sourceKey
    val routeKey = keys.routeKey
    val episodeKey = keys.episodeKey

    val episodes = detail.sourceDetails.firstOrNull { it.sourceKey == sourceKey }
        ?.routes?.firstOrNull { it.routeKey == routeKey }
        ?.episodes.orEmpty()
    val idx = episodes.indexOfFirst { it.episodeKey == episodeKey }
    val prev = if (idx > 0) episodes[idx - 1].episodeKey else null
    val next = if (idx in 0 until episodes.size - 1) episodes[idx + 1].episodeKey else null

    return Selection(detail, sourceKey, routeKey, episodeKey, prev, next)
}

private data class SelectionKeys(
    val detail: AggregatedDetail,
    val sourceKey: String,
    val routeKey: String?,
    val episodeKey: String?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    selection: Selection,
    isFavorite: Boolean,
    autoSelectSource: Boolean,
    sourceTier: (String) -> SourceTier,
    onToggleFavorite: () -> Unit,
    onSetAutoSelect: (Boolean) -> Unit,
    onSelectSource: (String) -> Unit,
    onSelectRoute: (String) -> Unit,
    onSelectEpisode: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = selection.detail
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // a. 片名 + 爱心
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // b. 自动选择可用源
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "自动选择可用源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoSelectSource, onCheckedChange = onSetAutoSelect)
        }

        // c. 选择源
        SectionTitle("选择源")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.sourceDetails.forEach { source ->
                SourceChip(
                    sourceName = source.sourceName,
                    tier = sourceTier(source.sourceKey),
                    selected = source.sourceKey == selection.sourceKey,
                    onClick = { onSelectSource(source.sourceKey) },
                )
            }
        }

        // d. 选择线路
        val currentSource = detail.sourceDetails.firstOrNull { it.sourceKey == selection.sourceKey }
        val routes = currentSource?.routes.orEmpty()
        if (routes.isNotEmpty()) {
            SectionTitle("选择线路")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                routes.forEach { route ->
                    ChoiceChip(
                        text = route.routeName,
                        selected = route.routeKey == selection.routeKey,
                        onClick = { onSelectRoute(route.routeKey) },
                    )
                }
            }
        }

        // e. 选集
        // FlowRow 自然撑高（不限高），交给外层 Column 的 verticalScroll 滚动，
        // 避免内层网格固定高度截断导致集数过多时底部看不全
        val episodes = routes.firstOrNull { it.routeKey == selection.routeKey }?.episodes.orEmpty()
        if (episodes.isNotEmpty()) {
            SectionTitle("选集")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 6,
            ) {
                val spacing = 8.dp
                episodes.forEachIndexed { index, episode ->
                    val selected = episode.episodeKey == selection.episodeKey
                    val routeKey = selection.routeKey
                    EpisodeCell(
                        label = (index + 1).toString(),
                        selected = selected,
                        // 6 列均分：每格占 (总宽 - 5 个间距) / 6，用 weight 实现等分
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (routeKey != null) {
                                onSelectEpisode(selection.sourceKey, routeKey, episode.episodeKey)
                            }
                        },
                    )
                }
                // 末行不足 6 个时补空占位，保持每格宽度与满行一致
                val remainder = episodes.size % 6
                if (remainder != 0) {
                    repeat(6 - remainder) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SourceChip(
    sourceName: String,
    tier: SourceTier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tierColor = tier.color()
    val tierLabel = tier.shortLabel
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tierColor,
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = border,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EpisodeCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = border,
        modifier = modifier
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
expect fun VideoPlayerArea(
    m3u8Url: String?,
    title: String,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onBack: () -> Unit,
    onPrevEpisode: (() -> Unit)?,
    onNextEpisode: (() -> Unit)?,
    onResolutionObserved: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
)
