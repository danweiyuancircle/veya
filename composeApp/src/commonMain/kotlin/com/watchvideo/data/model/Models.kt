package com.watchvideo.data.model

// Legacy UI/API 兼容模型。新 parser 输出模型定义在 SourceModels.kt。
data class SearchResult(
    val id: String,
    val title: String,
    val cover: String,
    val siteKey: String,
)

data class Episode(
    val name: String,
    val playUrl: String,
)

data class Route(
    val name: String,
    val episodes: List<Episode>,
)

data class PlayInfo(
    val m3u8Url: String,
    val title: String,
    val routeKey: String = "",
    val episodeKey: String = "",
    val headers: Map<String, String> = emptyMap(),
)
