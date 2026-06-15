package com.watchvideo.data.model

data class SourceSearchItem(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val subtitle: String?,
    val tags: List<String>,
    val detailUrl: String?,
) {
    val id: String get() = sourceContentId
    val siteKey: String get() = sourceKey
    val cover: String get() = coverUrl.orEmpty()
}

data class MetaBadge(
    val label: String,
    val value: String,
)

data class PlaybackEpisode(
    val episodeKey: String,
    val episodeLabel: String,
    val playPageUrl: String,
) {
    val name: String get() = episodeLabel
    val playUrl: String get() = playPageUrl
}

data class PlaybackRoute(
    val routeKey: String,
    val routeName: String,
    val episodes: List<PlaybackEpisode>,
) {
    val name: String get() = routeName
}

data class SourceDetail(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val summary: String?,
    val metadata: List<MetaBadge>,
    val routes: List<PlaybackRoute>,
)

data class PlaybackCandidate(
    val sourceKey: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val streamUrl: String,
    val headers: Map<String, String>,
) {
    val m3u8Url: String get() = streamUrl
}

fun SourceSearchItem.toLegacySearchResult(): SearchResult = SearchResult(
    id = sourceContentId,
    title = title,
    cover = coverUrl.orEmpty(),
    siteKey = sourceKey,
)

fun PlaybackEpisode.toLegacyEpisode(): Episode = Episode(
    name = episodeLabel,
    playUrl = playPageUrl,
)

fun PlaybackRoute.toLegacyRoute(): Route = Route(
    name = routeName,
    episodes = episodes.map { it.toLegacyEpisode() },
)

fun SourceDetail.toLegacyRoutes(): List<Route> = routes.map { it.toLegacyRoute() }

fun PlaybackCandidate.toLegacyPlayInfo(): PlayInfo = PlayInfo(
    m3u8Url = streamUrl,
    title = title,
    routeKey = routeKey,
    episodeKey = episodeKey,
    headers = headers,
)
