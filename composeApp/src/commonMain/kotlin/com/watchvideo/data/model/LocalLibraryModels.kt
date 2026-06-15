package com.watchvideo.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FailureType {
    SEARCH_EMPTY,
    SEARCH_ERROR,
    DETAIL_ERROR,
    DETAIL_EMPTY,
    PLAY_CANDIDATE_ERROR,
    PLAY_CANDIDATE_EMPTY,
    PLAYBACK_START_ERROR,
    PLAYBACK_INTERRUPTED,
}

@Serializable
data class SourceScoreRecord(
    val sourceKey: String,
    val baseScore: Int = 50,
    val searchSuccessCount: Int = 0,
    val searchFailureCount: Int = 0,
    val detailSuccessCount: Int = 0,
    val detailFailureCount: Int = 0,
    val playCandidateSuccessCount: Int = 0,
    val playCandidateFailureCount: Int = 0,
    val playbackStartSuccessCount: Int = 0,
    val playbackStartFailureCount: Int = 0,
    val consecutiveFailureCount: Int = 0,
    val avgFirstFrameMs: Long? = null,
    val stableObservedHeight: Int? = null,
    val maxObservedHeight: Int? = null,
    val lastFailureType: FailureType? = null,
    val lastFailureAtEpochMs: Long? = null,
    val cooldownUntilEpochMs: Long? = null,
)

@Serializable
data class WatchHistoryItem(
    val contentKey: String,
    val sourceKey: String,
    val sourceContentId: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String? = null,
    val episodeLabel: String,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedAtEpochMs: Long,
)

@Serializable
data class FavoriteItem(
    val contentKey: String,
    val title: String,
    val coverUrl: String? = null,
    val summary: String? = null,
    val preferredSourceKey: String? = null,
    val sourceContentId: String? = null,
    val favoriteAtEpochMs: Long,
)
