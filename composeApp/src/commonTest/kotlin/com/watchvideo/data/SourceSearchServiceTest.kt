package com.watchvideo.data

import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.Route
import com.watchvideo.data.model.SearchResult
import com.watchvideo.data.model.SourceSearchItem
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.toLegacyPlayInfo
import com.watchvideo.data.model.toLegacyRoutes
import com.watchvideo.data.model.toLegacySearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SourceSearchServiceTest {
    @Test
    fun normalized_search_item_keeps_source_identity() {
        val item = SourceSearchItem(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = null,
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

        assertEquals("modu", item.sourceKey)
        assertEquals("123", item.sourceContentId)
    }

    @Test
    fun source_search_item_is_not_the_same_runtime_type_as_legacy_search_result() {
        val item = SourceSearchItem(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = "http://localhost/poster.jpg",
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

        val legacy = item.toLegacySearchResult()

        assertEquals("123", legacy.id)
        assertEquals("modu", legacy.siteKey)
        assertFalse(SearchResult::class.isInstance(item))
    }

    @Test
    fun source_detail_is_not_a_legacy_route_list_and_can_bridge_explicitly() {
        val detail = SourceDetail(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = null,
            summary = null,
            metadata = emptyList(),
            routes = listOf(
                PlaybackRoute(
                    routeKey = "r1",
                    routeName = "线路1",
                    episodes = emptyList(),
                )
            ),
        )

        val legacyRoutes: List<Route> = detail.toLegacyRoutes()

        assertFalse(List::class.isInstance(detail))
        assertEquals("线路1", legacyRoutes.single().name)
    }

    @Test
    fun playback_candidate_bridge_keeps_route_episode_and_headers() {
        val legacy = PlaybackCandidate(
            sourceKey = "modu",
            routeKey = "route-a",
            episodeKey = "episode-3",
            title = "The Hero",
            streamUrl = "http://localhost/video.m3u8",
            headers = mapOf("Referer" to "http://localhost/detail"),
        ).toLegacyPlayInfo()

        assertEquals("http://localhost/video.m3u8", legacy.m3u8Url)
        assertEquals("route-a", legacy.routeKey)
        assertEquals("episode-3", legacy.episodeKey)
        assertEquals("http://localhost/detail", legacy.headers["Referer"])
    }
}
