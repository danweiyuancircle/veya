package com.watchvideo.ui.detail

import com.watchvideo.data.model.AggregatedDetail

/**
 * 详情页播放状态机（spec 4.2）。纯数据，无 Compose 依赖，可单测。
 */
sealed interface DetailPlaybackState {
    data object Idle : DetailPlaybackState

    data object LoadingDetail : DetailPlaybackState

    data class Ready(
        val detail: AggregatedDetail,
        val selectedSourceKey: String,
        val selectedRouteKey: String?,
        val selectedEpisodeKey: String?,
    ) : DetailPlaybackState

    data class ResolvingStream(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState

    data class Buffering(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState

    data class Playing(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
        val resolutionHeight: Int?,
    ) : DetailPlaybackState

    data class Recovering(
        val attemptedSourceKeys: List<String>,
        val attemptedRouteKeys: List<String>,
    ) : DetailPlaybackState

    data class Failed(val message: String) : DetailPlaybackState
}
