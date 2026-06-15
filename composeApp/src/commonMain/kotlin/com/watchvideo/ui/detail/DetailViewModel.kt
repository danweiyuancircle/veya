package com.watchvideo.ui.detail

import com.watchvideo.data.AggregatedDetailService
import com.watchvideo.data.ParserRegistry
import com.watchvideo.data.PlaybackRecoveryPolicy
import com.watchvideo.data.SiteParser
import com.watchvideo.data.local.FavoritesStore
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.local.WatchHistoryStore
import com.watchvideo.data.model.AggregatedDetail
import com.watchvideo.data.model.FavoriteItem
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.WatchHistoryItem
import com.watchvideo.platformEpochMs
import com.watchvideo.ui.theme.SourceTier
import com.watchvideo.ui.theme.tierOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel：状态机驱动的多源聚合播放。
 *
 * 状态流转见 [DetailPlaybackState]。loadDetail 加载聚合详情后进入 Ready，
 * 优先用观看历史恢复选中的源/线路/集数（spec 4.3），否则取评分最高源 + 首线路 + 首集。
 * 播放失败时用 [PlaybackRecoveryPolicy] 自动降级。
 */
class DetailViewModel(
    private val aggregatedDetailService: AggregatedDetailService,
    private val historyStore: WatchHistoryStore,
    private val favoritesStore: FavoritesStore,
    private val scoreStore: SourceScoreStore,
    private val parserLookup: (String) -> SiteParser = ParserRegistry::get,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job()),
) {
    private val _state = MutableStateFlow<DetailPlaybackState>(DetailPlaybackState.Idle)
    val state: StateFlow<DetailPlaybackState> = _state.asStateFlow()

    /** 当前播放流地址（供 VideoPlayerArea 使用）。 */
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    /** 自动选择可用源开关（UI 原型）。 */
    private val _autoSelectSource = MutableStateFlow(true)
    val autoSelectSource: StateFlow<Boolean> = _autoSelectSource.asStateFlow()

    /** 聚合详情，稳定数据；UI 据此渲染源/线路/选集，不依赖捕获瞬态 Ready。 */
    private val _detail = MutableStateFlow<AggregatedDetail?>(null)
    val detail: StateFlow<AggregatedDetail?> = _detail.asStateFlow()

    private var contentKey: String = ""
    private var currentSelectedSourceKey: String? = null
    private val tried = mutableSetOf<Triple<String, String, String>>()

    fun loadDetail(siteKey: String, id: String, title: String) {
        scope.launch {
            _state.value = DetailPlaybackState.LoadingDetail
            _streamUrl.value = null
            tried.clear()
            try {
                // 第一版仅加载当前源；全源聚合见 DONE_WITH_CONCERNS。
                val sourceDetail = parserLookup(siteKey).detail(id)
                val merged = aggregatedDetailService.merge(listOf(sourceDetail))
                // 解析不到标题时（如部分 HTML 源），用从搜索/历史带入的 title 兜底
                val aggregated = if (merged.title.isBlank() && title.isNotBlank()) {
                    merged.copy(title = title)
                } else {
                    merged
                }
                _detail.value = aggregated
                // 收藏 key 统一用聚合后的 contentKey（带 siteKey 前缀），与 toggleFavorite 一致
                contentKey = aggregated.contentKey
                _isFavorite.value = favoritesStore.isFavorite(aggregated.contentKey)
                val ready = readyFromHistoryOrDefault(aggregated, title)
                currentSelectedSourceKey = ready.selectedSourceKey
                _state.value = ready
                // 进入 Ready 后自动播放选中集（首次默认集 / 历史集），无需用户再手动点一次
                val routeKey = ready.selectedRouteKey
                val episodeKey = ready.selectedEpisodeKey
                if (routeKey != null && episodeKey != null) {
                    resolveAndPlay(aggregated, ready.selectedSourceKey, routeKey, episodeKey)
                }
            } catch (e: Exception) {
                _state.value = DetailPlaybackState.Failed("加载失败: ${e.message}")
            }
        }
    }

    private fun readyFromHistoryOrDefault(aggregated: AggregatedDetail, title: String): DetailPlaybackState.Ready {
        val history = historyStore.list().firstOrNull { it.contentKey == aggregated.contentKey }
        if (history != null) {
            val source = aggregated.sourceDetails.firstOrNull { it.sourceKey == history.sourceKey }
            val route = source?.routes?.firstOrNull { it.routeKey == history.routeKey }
            val episode = route?.episodes?.firstOrNull { it.episodeKey == history.episodeKey }
            if (source != null && route != null && episode != null) {
                return DetailPlaybackState.Ready(
                    detail = aggregated,
                    selectedSourceKey = source.sourceKey,
                    selectedRouteKey = route.routeKey,
                    selectedEpisodeKey = episode.episodeKey,
                )
            }
        }
        // 评分最高源（sourceDetails 已按评分降序）+ 首线路 + 首集
        val topSource = aggregated.sourceDetails.first()
        val firstRoute = topSource.routes.firstOrNull()
        return DetailPlaybackState.Ready(
            detail = aggregated,
            selectedSourceKey = topSource.sourceKey,
            selectedRouteKey = firstRoute?.routeKey,
            selectedEpisodeKey = firstRoute?.episodes?.firstOrNull()?.episodeKey,
        )
    }

    fun selectSource(sourceKey: String) {
        val aggregated = _detail.value ?: return
        val source = aggregated.sourceDetails.firstOrNull { it.sourceKey == sourceKey } ?: return
        val firstRoute = source.routes.firstOrNull()
        currentSelectedSourceKey = sourceKey
        _state.value = DetailPlaybackState.Ready(
            detail = aggregated,
            selectedSourceKey = sourceKey,
            selectedRouteKey = firstRoute?.routeKey,
            selectedEpisodeKey = firstRoute?.episodes?.firstOrNull()?.episodeKey,
        )
    }

    fun selectRoute(routeKey: String) {
        val aggregated = _detail.value ?: return
        // 无论当前处于哪种播放态（Playing/Buffering/ResolvingStream/Recovering/Ready），
        // 都用 currentSelectedSourceKey 推导，避免非 Ready 态时操作被丢弃。
        val sourceKey = currentSelectedSourceKey ?: return
        val source = aggregated.sourceDetails.firstOrNull { it.sourceKey == sourceKey } ?: return
        val route = source.routes.firstOrNull { it.routeKey == routeKey } ?: return
        _state.value = DetailPlaybackState.Ready(
            detail = aggregated,
            selectedSourceKey = sourceKey,
            selectedRouteKey = routeKey,
            selectedEpisodeKey = route.episodes.firstOrNull()?.episodeKey,
        )
    }

    fun selectEpisode(sourceKey: String, routeKey: String, episodeKey: String) {
        val aggregated = _detail.value ?: return
        scope.launch { resolveAndPlay(aggregated, sourceKey, routeKey, episodeKey) }
    }

    private suspend fun resolveAndPlay(
        aggregated: AggregatedDetail,
        sourceKey: String,
        routeKey: String,
        episodeKey: String,
    ) {
        tried.add(Triple(sourceKey, routeKey, episodeKey))
        _state.value = DetailPlaybackState.ResolvingStream(sourceKey, routeKey, episodeKey)
        val episode = findEpisode(aggregated, sourceKey, routeKey, episodeKey)
        if (episode == null) {
            _state.value = DetailPlaybackState.Failed("找不到该集数")
            return
        }
        try {
            val candidate = parserLookup(sourceKey).playInfo(episode.playPageUrl)
            _streamUrl.value = candidate.streamUrl
            // Buffering 是为后续播放器真实缓冲回调（Task 6）预留的状态，当前解析成功即视为可播放，
            // 故紧接着切入 Playing。勿删此状态。
            _state.value = DetailPlaybackState.Buffering(sourceKey, routeKey, episodeKey)
            _state.value = DetailPlaybackState.Playing(sourceKey, routeKey, episodeKey, resolutionHeight = null)
            recordHistory(aggregated, sourceKey, routeKey, episode)
        } catch (e: Exception) {
            recover(aggregated, sourceKey, routeKey, episodeKey)
        }
    }

    private suspend fun recover(
        aggregated: AggregatedDetail,
        sourceKey: String,
        routeKey: String,
        episodeKey: String,
    ) {
        val next = PlaybackRecoveryPolicy.nextCandidate(
            aggregated = aggregated,
            currentSourceKey = sourceKey,
            currentRouteKey = routeKey,
            currentEpisodeKey = episodeKey,
            tried = tried,
        )
        if (next == null) {
            _state.value = DetailPlaybackState.Failed("所有线路均不可用")
            return
        }
        _state.value = DetailPlaybackState.Recovering(
            attemptedSourceKeys = tried.map { it.first }.distinct(),
            attemptedRouteKeys = tried.map { it.second }.distinct(),
        )
        resolveAndPlay(aggregated, next.first, next.second, next.third)
    }

    private fun recordHistory(
        aggregated: AggregatedDetail,
        sourceKey: String,
        routeKey: String,
        episode: PlaybackEpisode,
    ) {
        val source = aggregated.sourceDetails.first { it.sourceKey == sourceKey }
        historyStore.upsert(
            WatchHistoryItem(
                contentKey = aggregated.contentKey,
                sourceKey = sourceKey,
                sourceContentId = source.sourceContentId,
                routeKey = routeKey,
                episodeKey = episode.episodeKey,
                title = aggregated.title,
                coverUrl = aggregated.preferredCoverUrl,
                episodeLabel = episode.episodeLabel,
                progressMs = 0,
                durationMs = 0,
                lastWatchedAtEpochMs = platformEpochMs(),
            )
        )
    }

    private fun findEpisode(
        aggregated: AggregatedDetail,
        sourceKey: String,
        routeKey: String,
        episodeKey: String,
    ) = aggregated.sourceDetails
        .firstOrNull { it.sourceKey == sourceKey }
        ?.routes?.firstOrNull { it.routeKey == routeKey }
        ?.episodes?.firstOrNull { it.episodeKey == episodeKey }

    /**
     * 播放器回填真实分辨率（Task 6）。仅在 Playing 态生效：更新角标显示的 resolutionHeight，
     * 并把真实 height 写入评分库的清晰度维度。
     */
    fun onResolutionObserved(height: Int) {
        if (height <= 0) return
        val playing = _state.value as? DetailPlaybackState.Playing ?: return
        if (playing.resolutionHeight == height) return   // 幂等：同分辨率不重复回填
        _state.value = playing.copy(resolutionHeight = height)
        // firstFrameMs 暂无真实测量值，传占位 0（见 DONE_WITH_CONCERNS）。
        scoreStore.recordPlaybackResolution(playing.sourceKey, firstFrameMs = 0, stableHeight = height)
    }

    fun setAutoSelectSource(enabled: Boolean) {
        _autoSelectSource.value = enabled
    }

    fun toggleFavorite() {
        val aggregated = _detail.value
        val key = aggregated?.contentKey ?: contentKey
        if (key.isEmpty()) return
        if (favoritesStore.isFavorite(key)) {
            favoritesStore.remove(key)
            _isFavorite.value = false
        } else {
            favoritesStore.add(
                FavoriteItem(
                    contentKey = key,
                    title = aggregated?.title ?: key,
                    coverUrl = aggregated?.preferredCoverUrl,
                    summary = aggregated?.preferredSummary,
                    preferredSourceKey = aggregated?.sourceDetails?.firstOrNull()?.sourceKey,
                    sourceContentId = aggregated?.sourceDetails?.firstOrNull()?.sourceContentId,
                    favoriteAtEpochMs = platformEpochMs(),
                )
            )
            _isFavorite.value = true
        }
    }

    /** 源 tier 分级，供 UI chip 标签/配色用。 */
    fun sourceTier(sourceKey: String): SourceTier = tierOf(scoreStore.rankValue(scoreStore.get(sourceKey)))
}
