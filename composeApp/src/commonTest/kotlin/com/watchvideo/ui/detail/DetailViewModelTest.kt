package com.watchvideo.ui.detail

import com.russhwolf.settings.Settings
import com.watchvideo.data.AggregatedDetailService
import com.watchvideo.data.SiteParser
import com.watchvideo.data.local.FavoritesStore
import com.watchvideo.data.local.SourceScoreStore
import com.watchvideo.data.local.WatchHistoryStore
import com.watchvideo.data.model.MetaBadge
import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.PlaybackEpisode
import com.watchvideo.data.model.PlaybackRoute
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceSearchItem
import com.watchvideo.data.model.WatchHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @Test
    fun detail_state_prefers_history_source_route_and_episode() = runTest {
        val settings = InMemorySettings()
        val historyStore = WatchHistoryStore(settings)
        historyStore.upsert(
            WatchHistoryItem(
                contentKey = "hero-2026",
                sourceKey = "modu",
                sourceContentId = "hero-2026",
                routeKey = "route-2",
                episodeKey = "ep-8",
                title = "英雄2026",
                coverUrl = null,
                episodeLabel = "第8集",
                progressMs = 1_000,
                durationMs = 10_000,
                lastWatchedAtEpochMs = 100,
            )
        )

        val viewModel = buildViewModel(
            scope = this,
            settings = settings,
            historyStore = historyStore,
            parser = FakeSiteParser(),
        )

        viewModel.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<DetailPlaybackState.Ready>(state)
        assertEquals("modu", state.selectedSourceKey)
        assertEquals("route-2", state.selectedRouteKey)
        assertEquals("ep-8", state.selectedEpisodeKey)
    }

    @Test
    fun detail_state_defaults_to_top_source_first_route_first_episode_without_history() = runTest {
        val settings = InMemorySettings()
        val viewModel = buildViewModel(
            scope = this,
            settings = settings,
            historyStore = WatchHistoryStore(settings),
            parser = FakeSiteParser(),
        )

        viewModel.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<DetailPlaybackState.Ready>(state)
        assertEquals("modu", state.selectedSourceKey)
        assertEquals("route-1", state.selectedRouteKey)
        assertEquals("ep-1", state.selectedEpisodeKey)
    }

    @Test
    fun toggle_favorite_persists_to_store() = runTest {
        val settings = InMemorySettings()
        val favoritesStore = FavoritesStore(settings)
        val viewModel = buildViewModel(
            scope = this,
            settings = settings,
            favoritesStore = favoritesStore,
            parser = FakeSiteParser(),
        )

        viewModel.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()

        assertTrue(!viewModel.isFavorite.value)
        viewModel.toggleFavorite()
        assertTrue(viewModel.isFavorite.value)
        assertTrue(favoritesStore.isFavorite("hero-2026"))
        viewModel.toggleFavorite()
        assertTrue(!viewModel.isFavorite.value)
    }

    @Test
    fun favorite_state_survives_reload_when_content_key_has_source_prefix() = runTest {
        // 真实 parser 的 contentKey 带 siteKey 前缀（"modu:hero-2026"）；
        // 回归:收藏后重新 loadDetail，isFavorite 必须仍为 true（曾因初始判断用裸 id 而恒 false）。
        val settings = InMemorySettings()
        val favoritesStore = FavoritesStore(settings)
        val parser = PrefixedFakeSiteParser()
        val first = buildViewModel(scope = this, settings = settings, favoritesStore = favoritesStore, parser = parser)

        first.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()
        first.toggleFavorite()
        assertTrue(first.isFavorite.value)
        assertTrue(favoritesStore.isFavorite("modu:hero-2026"))

        val second = buildViewModel(scope = this, settings = settings, favoritesStore = favoritesStore, parser = parser)
        second.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()
        assertTrue(second.isFavorite.value)
    }

    @Test
    fun select_route_during_playing_state_transitions_to_ready_with_new_route() = runTest {
        val settings = InMemorySettings()
        val viewModel = buildViewModel(
            scope = this,
            settings = settings,
            historyStore = WatchHistoryStore(settings),
            parser = FakeSiteParser(),
        )

        viewModel.loadDetail(siteKey = "modu", id = "hero-2026", title = "英雄2026")
        advanceUntilIdle()
        assertIs<DetailPlaybackState.Ready>(viewModel.state.value)

        // 进入 Playing 态
        viewModel.selectEpisode("modu", "route-1", "ep-1")
        advanceUntilIdle()
        assertIs<DetailPlaybackState.Playing>(viewModel.state.value)

        // 播放中切换线路，不应被静默丢弃
        viewModel.selectRoute("route-2")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertIs<DetailPlaybackState.Ready>(state)
        assertEquals("route-2", state.selectedRouteKey)
    }

    private fun buildViewModel(
        scope: CoroutineScope,
        settings: Settings,
        historyStore: WatchHistoryStore = WatchHistoryStore(settings),
        favoritesStore: FavoritesStore = FavoritesStore(settings),
        parser: SiteParser,
    ): DetailViewModel {
        val scoreStore = SourceScoreStore(settings) { 0L }
        return DetailViewModel(
            aggregatedDetailService = AggregatedDetailService(scoreStore),
            historyStore = historyStore,
            favoritesStore = favoritesStore,
            scoreStore = scoreStore,
            parserLookup = { parser },
            scope = scope,
        )
    }

    private class FakeSiteParser : SiteParser {
        override val siteKey: String = "modu"
        override val siteName: String = "模板影视"
        override val baseUrl: String = "http://localhost"

        override suspend fun search(keyword: String): List<SourceSearchItem> = emptyList()

        override suspend fun detail(id: String): SourceDetail = SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = id,
            sourceContentId = id,
            title = "英雄2026",
            coverUrl = null,
            summary = null,
            metadata = emptyList<MetaBadge>(),
            routes = listOf(
                PlaybackRoute(
                    routeKey = "route-1",
                    routeName = "线路1",
                    episodes = listOf(
                        PlaybackEpisode("ep-1", "第1集", "ep-1"),
                        PlaybackEpisode("ep-2", "第2集", "ep-2"),
                    ),
                ),
                PlaybackRoute(
                    routeKey = "route-2",
                    routeName = "线路2",
                    episodes = (1..10).map { PlaybackEpisode("ep-$it", "第${it}集", "ep-$it") },
                ),
            ),
        )

        override suspend fun playInfo(playPageUrl: String): PlaybackCandidate = PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "route-1",
            episodeKey = playPageUrl,
            title = "播放中",
            streamUrl = "http://localhost/$playPageUrl.m3u8",
            headers = mapOf("Referer" to baseUrl),
        )
    }

    /** 模拟真实 parser：contentKey 带 "siteKey:" 前缀。 */
    private class PrefixedFakeSiteParser : SiteParser {
        override val siteKey: String = "modu"
        override val siteName: String = "模板影视"
        override val baseUrl: String = "http://localhost"

        override suspend fun search(keyword: String): List<SourceSearchItem> = emptyList()

        override suspend fun detail(id: String): SourceDetail = SourceDetail(
            sourceKey = siteKey,
            sourceName = siteName,
            contentKey = "$siteKey:$id",
            sourceContentId = id,
            title = "英雄2026",
            coverUrl = null,
            summary = null,
            metadata = emptyList<MetaBadge>(),
            routes = listOf(
                PlaybackRoute(
                    routeKey = "route-1",
                    routeName = "线路1",
                    episodes = listOf(PlaybackEpisode("ep-1", "第1集", "ep-1")),
                ),
            ),
        )

        override suspend fun playInfo(playPageUrl: String): PlaybackCandidate = PlaybackCandidate(
            sourceKey = siteKey,
            routeKey = "route-1",
            episodeKey = playPageUrl,
            title = "播放中",
            streamUrl = "http://localhost/$playPageUrl.m3u8",
            headers = emptyMap(),
        )
    }

    private class InMemorySettings : Settings {
        private val data = mutableMapOf<String, Any>()

        override val keys: Set<String> get() = data.keys
        override val size: Int get() = data.size

        override fun clear() = data.clear()
        override fun remove(key: String) { data.remove(key) }
        override fun hasKey(key: String): Boolean = data.containsKey(key)
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = data[key] as? Int ?: defaultValue
        override fun getIntOrNull(key: String): Int? = data[key] as? Int
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun getLong(key: String, defaultValue: Long): Long = data[key] as? Long ?: defaultValue
        override fun getLongOrNull(key: String): Long? = data[key] as? Long
        override fun putString(key: String, value: String) { data[key] = value }
        override fun getString(key: String, defaultValue: String): String = data[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String): String? = data[key] as? String
        override fun putFloat(key: String, value: Float) { data[key] = value }
        override fun getFloat(key: String, defaultValue: Float): Float = data[key] as? Float ?: defaultValue
        override fun getFloatOrNull(key: String): Float? = data[key] as? Float
        override fun putDouble(key: String, value: Double) { data[key] = value }
        override fun getDouble(key: String, defaultValue: Double): Double = data[key] as? Double ?: defaultValue
        override fun getDoubleOrNull(key: String): Double? = data[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key] as? Boolean ?: defaultValue
        override fun getBooleanOrNull(key: String): Boolean? = data[key] as? Boolean
    }
}
